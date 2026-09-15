## Context

部门是典型树形结构数据。现有系统（用户/角色/菜单）已建立稳定的模式：后端 `XxxAdminService` + controller + `@RequirePermission`，前端 `useVbenVxeGrid` + `useVbenModal` + `useVbenForm`。本变更完全复用这些模式，不引入新依赖。

## Goals / Non-Goals

- Goals：部门 CRUD + 树形约束 + 用户归属部门；前后端权限码闭环
- Non-Goals：不做部门级别的数据权限（行级过滤）、不做多部门归属、不做部门负责人管理页面（仅存字段）

## Decisions

### D1：树形存储用 parentId 自引用（不用 path/编码前缀）
- 数据量小（组织机构通常 < 数百节点），parentId + 递归组装足够；与菜单 `sys_menu` 的树形实现保持一致，降低心智负担
- 防环校验在 service 层做：沿新 parentId 向上遍历祖先链，出现自身 id 即拒绝

### D2：删除校验用两次查询而非外键
- 删除前分别查 `sys_dept` 子节点数与 `sys_dept` 被 `sys_user.dept_id` 引用数，任一 > 0 返回 400
- 与现有模块风格一致（H2 建表用 MyBatis-Plus schema 初始化，不依赖数据库外键）

### D3：用户表加 deptId 可空列
- `sys_user` 增加 `dept_id BIGINT NULL`；实体/详情/保存/分页列表联动
- 列表展示部门名通过 service 层批量查部门名后填充（不引入 join mapper XML，保持纯 MyBatis-Plus 风格）

### D4：接口与权限码
- `GET  /api/system/dept/list`（`System:Dept:List`，返回嵌套树）
- `POST /api/system/dept/save`（`System:Dept:Add`）
- `POST /api/system/dept/update`（`System:Dept:Edit`）
- `POST /api/system/dept/delete/{id}`（`System:Dept:Delete`）
- 与现有 user/role/menu 控制器风格完全一致

### D5：前端页面结构
- `views/system/dept/index.vue`：useVbenVxeGrid 树形模式（childrenField: 'children'），工具栏「新增根部门」挂 `System:Dept:Add`
- `views/system/dept/dept-form.vue`：useVbenModal + useVbenForm，父部门用 TreeSelect（部门树排除自身及子孙，防环在前端先行提示）
- `user-form.vue` 增加部门 TreeSelect 字段（可选），列表增加部门名列

### D6：种子数据
- 部门树：总公司(1) → 研发部/运营部/仓储部；仓储部挂 stockAdmin，演示按部门归属
- 菜单：系统管理下加「部门管理」C 菜单 + 4 个 F 按钮权限（权限码对齐 D4）；super 角色自动全量授权（沿用菜单保存时的自动授权逻辑，种子直接插关联）

## Risks / Trade-offs

- H2 文件库带旧数据时需重置（`data/*.mv.db`）以获得新表结构 —— 一次性成本，已在任务清单标注
- parentId 自引用在超大组织树上防环需递归查询 —— 当前规模可接受，SaaS 阶段如有性能诉求再引入 path 物化

## Migration Plan

1. 建表 `sys_dept`、`sys_user` 加列（H2 schema 重建）
2. 后端实体/服务/控制器 → 种子数据
3. 前端 API/页面/用户表单联动
4. 重置数据库 → 启动验证 → 验收场景逐条过

## Open Questions

- 无（部门负责人仅存名称字段，暂不做人员选择器，如需可后续变更）
