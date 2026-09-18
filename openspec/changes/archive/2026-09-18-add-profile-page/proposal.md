# add-profile-page 个人中心页面（前后端）

## Why

头部用户下拉已有「个人中心」入口，但前端从未注册 `Profile` 路由，点击直接 404；`views/_core/profile/` 下的四个 tab 全是模板空壳（改密码只弹假 success 不调接口、基本设置用 mock 角色和后端不存在的字段、安全设置/新消息提醒为纯假数据）。用户缺少自助维护个人信息（头像、昵称、简介）与修改密码的入口，目前昵称只能由管理员在用户管理中代改。

## What Changes

- 后端 `sys_user` 新增 `introduction`（个人简介）列：schema-postgres.sql / schema-mysql.sql 增量加列 + `SysUser` 实体补字段
- 后端 `UserController` 新增 `PATCH /user/profile`：登录即可、无需权限码，仅能修改本人 `nickname` + `introduction`（nickname @NotBlank）
- 后端 `GET /user/info` 返回补充 `introduction` 字段
- 前端注册 `Profile` 路由（core.ts，BasicLayout 下，登录即可）
- 前端重写基本设置 tab：头像点击上传（复用 `POST /file/avatar`）+ 昵称/简介编辑 + 用户名/角色只读展示，删除 mock 角色下拉
- 前端重写修改密码 tab：调真实 `POST /auth/change-password`，成功后因 token 全部失效而跳转登录页
- 前端 `api/core/auth.ts` 补 `changePasswordApi`，新增 `updateProfileApi`
- 前端删除假数据的 security-setting.vue 与 notification-setting.vue，tabs 只留「基本设置」「修改密码」

## Capabilities

### New Capabilities

- `system/user-profile`: 用户个人中心——本人自助查看/维护个人信息（头像、昵称、简介）与修改密码

### Modified Capabilities

（无——现有能力的需求不变，file-storage 的头像上传与 session-control 的改密失效语义均按既有规格复用）

## Impact

- 后端：`SysUser` 实体、`UserController`、`db/schema-postgres.sql`、`db/schema-mysql.sql`（PostgreSQL 需对已有库生效，MySQL 保持同构）
- 前端：`front/apps/web-ele` 的 `router/routes/core.ts`、`views/_core/profile/`、`api/core/auth.ts`、新增 `api/core/user.ts`（或并入现有 api 结构）
- 复用接口（不改）：`POST /file/avatar`、`POST /auth/change-password`（含改密 bump token 版本号的既有行为）
