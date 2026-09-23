## Why

用户需要通过「创建账号」完成真实注册。当前页面仅提示联系管理员，系统已有 Redis 一次性图形验证码，可复用以保护公开注册接口。

## What Changes

- 新增公开注册接口，校验用户名、密码、确认密码和图形验证码，使用 BCrypt 保存密码。
- 注册接口增加按 IP 限流、Redis 防重复提交及用户名唯一键冲突处理。
- 注册页使用 Vben 注册表单及验证码弹窗，错误后刷新验证码，成功后返回登录、提示成功并回填用户名。
- 新账号启用但不分配角色、部门或管理权限，登录首页为个人中心，由管理员后续授权。

## Capabilities

### New Capabilities

- `security/self-registration`：通过图形验证码自助注册及最小权限初始化。

### Modified Capabilities

- `security/login-guard`：创建账号入口由说明页升级为实际注册，保持页面正常往返和登录状态清理。

## Impact

- 后端认证 Controller、专用 DTO 与注册 Service、JWT 公开路径、中英错误文案及测试。
- 前端认证 API、注册页、登录页、认证 store 的一次性用户名交接、中英语言包。
- 复用 sys_user 及现有唯一约束，无新菜单、权限码、角色或表结构；更新 README。
