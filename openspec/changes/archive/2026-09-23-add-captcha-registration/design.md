## Context

CaptchaService 以 Redis GETDEL 校验，验证码两分钟有效、忽略大小写、校验即销毁。sys_user.username 在 PostgreSQL/MySQL 均有唯一约束。现有管理员创建用户接口允许分配角色，不适合作为公开注册入参。

## Goals / Non-Goals

- Goals：匿名用户可注册并登录，验证码不可重放，并发同名注册不产生重复账号；新账号无管理权限，前端失败可重试、成功可返回登录。
- Non-Goals：短信或邮件验证码、自动角色授权、注册审批、自动登录和修改既有登录密码规则。

## Decisions

- 新建专用 RegisterRequest，只接受 username/password/confirmPassword/captchaId/captchaCode，不绑定 SysUser，忽略额外角色、状态和部门字段。
- 用户名为 4–32 位英文字母、数字、下划线且以字母开头。密码为 8–64 位 ASCII 可见字符，至少包含字母和数字；禁止空白，避免 BCrypt 的 72 字节限制及不可见字符歧义。前后端规则一致。
- 在注册 Service 中先比对确认密码、校验验证码，再检查用户名并插入。捕获唯一键并发冲突并返回已有用户名的业务错误；不返回用户实体、密码、Token 或 Cookie。
- 使用独立注册限流键（每 IP 每分钟 5 次），防重窗口 3 秒。复用现有 Redis 切面，Redis 不可用时停止注册。
- 新用户 status=0，nickname=username，homePath=/profile，角色和部门为空；不复用可由管理员扩权的 user 角色。
- 前端复用 AuthenticationRegister 和 useVbenModal/useVbenForm；先校验注册字段，再取验证码。验证码获取失败可刷新重试；提交失败保留注册资料、清空并更换验证码。请求期间禁用重复确认。
- 成功后仅将用户名保存在 auth store 的一次性内存字段，返回登录页后清除；登录组件在挂载后回填用户名并保持密码为空，优先于旧记住账号。密码不写入 URL、存储或日志。

## Risks

- 公开注册会增加账号数量，验证码、按 IP 限流和数据库唯一约束共同限制滥用。
- 同一出口 IP 的并发用户共享 3 秒防重窗口，界面明确显示重复提交/限流错误并允许重新获取验证码。
- 新账号没有业务角色，只获得既有登录用户基础能力；权限分配仍需管理员完成。
- 后端源码更新需重启运行服务才会生效，验证时使用隔离测试与本地服务检查。
