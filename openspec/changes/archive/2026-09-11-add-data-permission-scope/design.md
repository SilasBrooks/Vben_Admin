# 技术设计：add-data-permission-scope

## Context

- 部门树已落地（`sys_dept` parent_id 自引用），`sys_user.dept_id` 可空（NULL=未归属）；用户列表查询使用 MyBatis-Plus `LambdaQueryWrapper`（keyword/status 条件），无 XML SQL
- 权限模型：`@RequirePermission` 注解 + 拦截器做功能权限；用户详情由 `SecurityContextProvider` 提供当前用户与角色集合
- 数据库为 PostgreSQL（dev），schema 幂等初始化；MySQL schema 需同步
- 参考模型：若依 RuoYi 的 data_scope 方案，但其基于 XML SQL `${params.dataScope}` 字符串拼接——本项目为 Wrapper 风格，需等效转换

## Goals / Non-Goals

**Goals:**
- 角色维度 5 档数据范围（1全部/2自定义/3本部门/4本部门及以下/5仅本人），多角色取并集
- 服务层强制过滤、声明式复用（后续库存等模块一行注解接入）
- 角色管理前端可配置 dataScope 与自定义部门树

**Non-Goals:**
- 不做数据权限的"行级自定义 SQL 规则引擎"（只做部门维度）
- 不改动部门管理/菜单权限既有行为
- 不为查询明细接口（如 GET /user/{id}）做范围校验（列表过滤为主，详情访问已受功能权限约束；列表页点开的详情必然来自可见范围）

## Decisions

### D1：过滤条件注入方式 —— 切面计算 + 服务读取（Wrapper 追加），不用 XML 拼接
- `@DataScope(deptField = "deptId", userField = "id")` 注解标注在 service 的列表方法上
- `DataScopeAspect`（@Around）在方法执行前调用 `DataScopeService` 解析当前用户的**可见部门集合**（`Set<Long>`）与**是否包含未归属数据**，放入 `DataScopeHolder`（ThreadLocal，方法结束 finally 清理）
- service 内部构建 Wrapper 时调用 `DataScopeHolder.require()` 拿到过滤条件对象并追加：
  - 全部数据 → 不加条件
  - 部门类范围 → `deptId IN (deptIds)` 或（includeUnassigned 时）`deptId IN (...) OR deptId IS NULL`
  - 仅本人 → `userId = 当前用户`
- **为何不用**：MyBatis-Plus `DataPermissionInterceptor`/JSqlParser 改写 SQL——能力强但对 Wrapper/别名字段映射脆弱、调试困难；本项目查询集中在少数 service，显式追加条件可读性最好。**为何不用 XML ${dataScope}**：若依式字符串拼接有注入风险且与本项目 Wrapper 风格不符
- ThreadLocal 仅在切面生命周期内存活（@Around 内同步调用，finally remove），无异步泄漏风险

### D2：部门集合预展开，不做 SQL 递归
- `DataScopeService.resolveDeptIds(user)` 返回最终 `Set<Long>`：范围 3 → {本部门}；范围 4 → 本部门+递归子孙（内存中基于全量部门树展开，部门表量级小）；范围 2 → 关联表配置的部门+递归子孙（与范围 4 一致语义：选父即含子）
- **为何不用**：PG 8.0 不涉及，MySQL 8 有 `WITH RECURSIVE`——但双数据库语法差异与 Wrapper 兼容性不值当；全量部门树已在内存缓存（菜单/部门种子量级 < 千）

### D3：多角色并集与全部放行
- 逐一解析用户的每个有效角色：任一 dataScope=1 → 立即返回"全部"标记；否则合并各角色的 deptIds 集合，任一角色含未归属数据则 includeUnassigned=true；任一角色为 5（仅本人）→ 加入"本人 id"作为兜底可见项
- 超级管理员（username=vben 或内置 super 角色）直接放行，与功能权限的超管逻辑一致

### D4：表结构 —— sys_role 加列 + sys_role_dept 关联表
- `sys_role.data_scope` CHAR(1)/SMALLINT，默认 '5'
- `sys_role_dept(id, role_id, dept_id)`，UNIQUE(role_id, dept_id)，仅 data_scope=2 时读写
- 两个 schema（postgres/mysql）同步，种子数据迁移：super/admin=1，user=5，stockAdmin 所属角色=4

### D5：角色接口与前端
- `GET /system/role/detail`（或现有详情接口）返回 `dataScope` + `deptIds`（仅范围 2 时非空）；新增 `PUT /system/role/dataScope` 接收 `{roleId, dataScope, deptIds[]}`，权限复用 `System:Role:Auth`
- `role/form.vue` 增加"数据范围"RadioGroup（5 档中文），选"自定义部门"时展示部门 TreeSelect 多选（复用部门树接口，带父子联动）；`role/index.vue` 加"数据范围"列（字典式映射，不新建字典，前端常量映射即可）

## Risks / Trade-offs

- [业务代码忘记读取 DataScopeHolder 导致过滤失效] → 首个接入点（用户列表）作为模板代码固化；tasks 中要求每个新接入点写越权验收用例
- [部门树全量展开在超大组织下的内存/性能] → 当前量级（<1千部门）无感知；预留后续切缓存/懒加载的接口边界（resolveDeptIds 单方法）
- [范围 2/4 包含子孙部门语义与若依"仅配置部门本身"不一致] → 明确采用"选父含子"语义并写入 spec，避免使用方误解
- [dept_id IS NULL 数据对受限角色可见] → 防止"数据消失"的设计取舍，已在 spec 中固化；若业务后续要求隐藏，改 includeUnassigned 一处即可

## Migration Plan

1. DDL：两个 schema 追加列与关联表（幂等），启动自动执行
2. 种子：DatabaseSeeder 补 data_scope（空库自动；存量库 data_scope 默认 '5' 兜底，管理员手动调角色表单设置）
3. 回滚：删除新列/新表即可，功能代码对旧表结构无侵入（列缺失仅新功能不生效）

## Open Questions

无——data_scope 取值语义、级联口径（选父含子）、并集规则已在 specs 固化。
