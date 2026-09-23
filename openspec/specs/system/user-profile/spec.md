# system/user-profile Specification

## Purpose
允许已登录用户在个人中心维护本人的头像、昵称、简介和联系信息，验证旧密码后修改登录密码，并通过当前密码与邮件验证码绑定安全邮箱，为后续密码找回提供已验证的邮箱凭据。

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

前端 MUST 注册 `Profile` 路由（BasicLayout 下，登录即可访问，无需权限码），页面包含「基本设置」「修改密码」和「安全邮箱」三个 tab。

基本设置 tab：展示头像（点击可上传新头像，复用 `POST /file/avatar`，上传成功后刷新 store 头像）、昵称（可编辑）、个人简介（可编辑）、用户名（只读）、角色（只读）。修改资料 MUST 调用 `PATCH /user/profile`。

修改密码 tab：表单含旧密码/新密码/确认密码，提交 MUST 调用 `POST /auth/change-password`，成功后提示「密码已修改，请重新登录」并跳转登录页。

安全邮箱 tab：展示绑定状态和邮箱。启用邮件验证后，未绑定用户 MUST 直接显示绑定表单；已绑定用户 MUST 默认隐藏绑定表单并显示「更换安全邮箱」按钮，点击后再展示表单。表单 MUST 同屏显示当前密码、目标邮箱及邮件验证码输入，验证码输入框右侧 MUST 显示发送邮箱验证码按钮，底部 MUST 显示独立绑定按钮。发送仅校验邮箱和当前密码；发送成功后开始冷却并允许用户填写收到的验证码，点击绑定才提交确认。修改邮箱或当前密码 MUST 清除旧挑战和验证码；发送或绑定期间 MUST 阻止重复操作及修改输入；发送失败 MUST 保留表单输入，不能声称成功。绑定或更换成功并刷新邮箱状态后 MUST 收起表单，展示已绑定状态和更换入口。只有通过密码和邮箱验证才能绑定或更换邮箱，未配置发信时明确提示联系管理员。普通联系邮箱 MUST NOT 自动视为已验证的安全邮箱。

#### Scenario: 从头部下拉进入个人中心

- **WHEN** 已登录用户点击头部用户下拉中的「个人中心」
- **THEN** 路由跳转到 `Profile` 页面，默认显示「基本设置」tab，展示当前用户信息

#### Scenario: 上传头像后头部即时刷新

- **WHEN** 用户在基本设置 tab 上传新头像成功
- **THEN** 页面头像即时更换为新头像，头部导航的头像也同步更新

#### Scenario: 修改密码成功跳转登录

- **WHEN** 用户在修改密码 tab 提交正确旧密码和新密码
- **THEN** 弹出成功提示，本地 token 与用户信息被清除，路由跳转到登录页

#### Scenario: 邮箱绑定成功

- **WHEN** 用户在安全邮箱 tab 完成密码及邮件验证码验证并点击绑定，绑定成功且邮箱状态刷新成功
- **THEN** 显示已绑定状态和成功提示，收起表单并显示「更换安全邮箱」按钮，后续密码找回仅向该安全邮箱发送验证码

#### Scenario: 进入安全邮箱表单

- **WHEN** 邮件验证已启用，未绑定安全邮箱的用户打开安全邮箱 tab
- **THEN** 直接显示邮箱、密码、验证码输入，验证码输入右侧显示发送按钮，底部显示绑定按钮；未成功发码前绑定按钮禁用

#### Scenario: 已绑定用户进入安全邮箱页面

- **WHEN** 邮件验证已启用，已绑定安全邮箱的用户打开或重新进入安全邮箱 tab
- **THEN** 显示当前已绑定邮箱和「更换安全邮箱」按钮，默认不显示绑定表单

#### Scenario: 主动更换安全邮箱

- **WHEN** 已绑定用户点击「更换安全邮箱」按钮
- **THEN** 显示完整绑定表单，隐藏更换入口和上次成功提示；用户须通过当前密码和新邮箱验证码验证后才能更换

#### Scenario: 修改目标信息后重新发码

- **WHEN** 发码成功后用户修改目标邮箱或当前密码
- **THEN** 清空旧挑战和验证码，禁用绑定，保留发送冷却；冷却结束可在原表单再次发码

#### Scenario: 发信失败后修正重试

- **WHEN** 发码接口返回失败
- **THEN** 显示错误且保留邮箱、密码及验证码输入，结束发送加载状态，允许重新发送

#### Scenario: 邮件验证未启用

- **WHEN** 用户打开安全邮箱 tab 且邮件验证未启用
- **THEN** 显示绑定状态及联系管理员的提示，不展示绑定表单或更换按钮
