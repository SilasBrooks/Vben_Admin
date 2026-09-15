## Why

当前系统已有用户/角色/菜单管理，但缺少部门（组织机构）管理。用户无法归属部门，后续的数据权限（按部门隔离数据）、部门级角色授权都无法开展。部门是通用后台模板的必备基础能力，也是在通往 SaaS 多租户之前必须夯实的基础数据。

## What Changes

- 新增部门表 `sys_dept`（树形结构，支持无限层级）
- 后端新增部门管理模块：树形查询、新增、修改、删除接口，权限码体系对齐现有 `System:Xxx:Yyy` 约定
- 前端新增「部门管理」页面：树形表格展示 + 新增/编辑/删除（vben 组件体系）
- 用户管理模块增加「所属部门」字段：表单可选部门（部门树选择器）、列表展示部门名
- 种子数据：`DatabaseSeeder` 写入默认部门树（总公司 → 研发部/运营部 等）并给 super 角色授权部门管理权限码
- 菜单种子：系统管理下新增「部门管理」菜单（C 类型）与按钮权限（F 类型）

不改变现有用户/角色/菜单模块的既有行为。

## Capabilities

### New Capabilities

- `system/dept`: 部门管理能力——部门树形结构的增删改查，包含树形约束（父部门合法性、删除须无子部门且无关联用户）、权限码控制（`System:Dept:List/Add/Edit/Delete`）及前后端交互契约

### Modified Capabilities

- （尚无已归档规格。用户管理增加部门关联属于新增行为的扩展，随 `system/dept` 一并在 delta 中以需求形式声明，待首个变更归档后形成 `system/user` 规格基线）

## Impact

- **后端**：`service/` 新增 `SysDept` entity/mapper/`SysDeptAdminService`/`SystemDeptController`；`SysUser` entity 增加 `deptId` 字段（H2 数据库重建即可，暂不涉及线上迁移）；`SysUserAdminService` 查询/保存联动部门；`DatabaseSeeder` 增加部门与权限种子
- **前端**：`front/apps/web-ele` 新增 `views/system/dept/`（index.vue + dept-form.vue）与 `api/system/dept.ts`；`views/system/user/user-form.vue` 增加部门选择字段；菜单/按钮权限种子同步
- **权限**：新增权限码 `System:Dept:List/Add/Edit/Delete`，super 角色默认全量授权；stockAdmin 不受影响
- **依赖**：无新增第三方依赖
