# system/user-profile Specification

## Purpose
允许已登录用户自助查看与维护本人个人信息（头像、昵称、简介）以及修改密码，无需管理员介入。

## Requirements

### Requirement: 查看本人信息

已登录用户 MUST 能通过 `GET /user/info` 获取本人当前信息（id、username、realName、roles、avatar、introduction）。`introduction` 为可空字段，未设置时返回空字符串而非 null。

#### Scenario: 已设置简介和头像

- **WHEN** 已登录用户请求 `GET /user/info` 且其 `nickname`、`avatar`、`introduction` 均已设置
- **THEN** 响应 200，`realName` = 用户昵称，`avatar` = `/api/file/{fileId}/content`，`introduction` = 个人简介文本

#### Scenario: 未设置简介

- **WHEN** 已登录用户请求 `GET /user/info` 且 `introduction` 为 null
- **THEN** 响应 200，`introduction` 返回空字符串 `""`

#### Scenario: 未设置头像

- **WHEN** 已登录用户请求 `GET /user/info` 且 `avatar` 为 null
- **THEN** 响应 200，响应体不含 `avatar` 字段

### Requirement: 修改本人资料

已登录用户 MUST 能通过 `PATCH /user/profile` 修改本人 `nickname` 和 `introduction`。该接口无需权限码，仅能修改本人资料，不可改 username、password、avatar（avatar 走头像上传接口）。

`nickname` MUST NOT 为空白（@NotBlank 校验，至少 1 字符），`introduction` 可空。

#### Scenario: 正常修改昵称与简介

- **WHEN** 已登录用户以 `{ "nickname": "新昵称", "introduction": "自我介绍" }` 请求 `PATCH /user/profile`
- **THEN** 响应 200，`GET /user/info` 返回的 `realName` = `新昵称`、`introduction` = `自我介绍`，`updateTime` 被自动刷新

#### Scenario: 昵称为空被拒

- **WHEN** 已登录用户以 `{ "nickname": "", "introduction": "..." }` 请求 `PATCH /user/profile`
- **THEN** 响应 400，错误信息提示 nickname 不能为空

#### Scenario: 修改不携带 token

- **WHEN** 未携带 Bearer token 请求 `PATCH /user/profile`
- **THEN** 响应 401

### Requirement: 修改密码后令牌失效

修改密码成功后，系统 MUST 通过 token 版本号 bump 使本人已签发的所有 access token 立即失效。前端 MUST 在调用成功后清除本地登录态并跳转登录页。

#### Scenario: 修改密码成功

- **WHEN** 已登录用户以正确的旧密码和符合长度要求的新密码（至少 6 位）请求 `POST /auth/change-password`
- **THEN** 响应 200，该用户之前签发的 access token 立即失效（后续请求返回 401）

#### Scenario: 旧密码错误

- **WHEN** 已登录用户以错误的旧密码请求 `POST /auth/change-password`
- **THEN** 响应 400，错误信息提示「旧密码不正确」，token 不受影响

### Requirement: 前端个人中心页面

前端 MUST 注册 `Profile` 路由（BasicLayout 下，登录即可访问，无需权限码），页面包含「基本设置」和「修改密码」两个 tab。

基本设置 tab：展示头像（点击可上传新头像，复用 `POST /file/avatar`，上传成功后刷新 store 头像）、昵称（可编辑）、个人简介（可编辑）、用户名（只读）、角色（只读）。修改资料 MUST 调用 `PATCH /user/profile`。

修改密码 tab：表单含旧密码/新密码/确认密码，提交 MUST 调用 `POST /auth/change-password`，成功后提示「密码已修改，请重新登录」并跳转登录页。

#### Scenario: 从头部下拉进入个人中心

- **WHEN** 已登录用户点击头部用户下拉中的「个人中心」
- **THEN** 路由跳转到 `Profile` 页面，默认显示「基本设置」tab，展示当前用户信息

#### Scenario: 上传头像后头部即时刷新

- **WHEN** 用户在基本设置 tab 上传新头像成功
- **THEN** 页面头像即时更换为新头像，头部导航的头像也同步更新

#### Scenario: 修改密码成功跳转登录

- **WHEN** 用户在修改密码 tab 提交正确旧密码和新密码
- **THEN** 弹出成功提示，本地 token 与用户信息被清除，路由跳转到登录页
