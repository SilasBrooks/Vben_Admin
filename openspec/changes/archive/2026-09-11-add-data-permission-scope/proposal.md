# 变更提案：add-data-permission-scope

## Why

系统目前只有**功能权限**（能调哪些接口），没有**数据权限**（能看到哪些范围的数据）：任何有 `System:User:List` 权限的角色，无论归属哪个部门，都能查到全公司所有用户。部门管理已落地（sys_user.dept_id 已就位），数据权限是通用后台模板底座的最后一块，也是后续"部门级角色授权"的前提。

## What Changes

- `sys_role` 增加 `data_scope` 字段（数据范围）：1=全部数据、2=自定义部门、3=本部门、4=本部门及以下、5=仅本人
- 新增 `sys_role_dept` 关联表：data_scope=2（自定义）时记录角色可见的部门集合
- 新增 `@DataScope` 注解 + AOP 切面：按当前登录用户的角色数据范围，自动为列表查询注入部门过滤条件（MyBatis-Plus Wrapper 追加 `dept_id IN (...)` / `dept_id IS NULL OR id = 当前用户` 等条件）
- 首个应用点：**用户管理列表/导出查询**按数据范围过滤；提供 `DataScopeService` 供后续模块（库存等）复用
- 角色管理前端表单增加"数据范围"下拉（选择"自定义部门"时出现部门树多选）；角色列表显示数据范围
- 角色管理接口：更新角色的 data_scope / 自定义部门集合（`System:Role:Auth` 权限复用）
- 种子数据：super/admin=全部数据；user=仅本人；stockAdmin 所在角色=本部门及以下（演示数据隔离效果）

## Capabilities

### New Capabilities

- `system/data-scope`: 数据范围过滤能力——角色 data_scope 语义、@DataScope 注解过滤行为、各范围（全部/自定义/本部门/及以下/仅本人）的可见数据边界、未归属部门用户的行为

### Modified Capabilities

- `system/role`: 角色新增 data_scope 属性与自定义部门集合的管理要求（表单必填、列表展示、更新校验）

## Impact

- **后端**：`SysRole` 实体/表结构（新列）、新表 `sys_role_dept`、新 `common/` 注解+切面、`system/scop` 服务层、`SystemRoleController`（角色详情返回 dataScope/自定义部门列表）、`SysUserAdminService`（列表接入过滤）、H2→`schema-postgres.sql`/`schema-mysql.sql` 同步
- **前端**：`role/form.vue`（数据范围下拉+部门树）、`role/index.vue`（列展示）、`api/system/role.ts`（类型与字段）
- **种子数据**：`DatabaseSeeder` 角色种子补充 data_scope，stockAdmin 演示数据隔离
- **无破坏性变更**：现有接口响应字段只增不改；未配置 data_scope 的既有角色按"仅本人"兜底
