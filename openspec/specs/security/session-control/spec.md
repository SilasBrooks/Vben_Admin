# security/session-control Specification

## Purpose
为 JWT 无状态认证补上"服务端可控失效"能力：通过 Redis 中的用户 token 版本号，使凭证变更（改密/重置密码/禁用）与管理员强制下线动作能立即作废已签发的 accessToken 与 refreshToken，并提供在线会话的全生命周期登记。

## Requirements

### Requirement: token 版本号即时失效

系统 MUST 在 Redis 中为每个用户维护当前 token 版本号（无记录视为 0）；签发 access/refresh token 时 MUST 将当时版本写入 `ver` 声明；认证过滤器 MUST 校验 token 载荷版本与 Redis 当前版本一致，不一致 MUST 拒绝（401）。无 `ver` 声明的历史 token MUST 视为版本 0（兼容存量会话）。以下动作 MUST 将该用户版本号 +1：用户修改自己的密码成功、管理员重置密码成功、用户被禁用（status 置停用）、管理员强制下线。

#### Scenario: 改密后旧 accessToken 失效

- **WHEN** 用户修改密码成功后，携带修改前签发的 accessToken 访问任意需认证接口
- **THEN** 返回 401，该 token 无法再使用；用新密码重新登录获得的新 token 正常访问

#### Scenario: 禁用用户后旧 token 失效

- **WHEN** 管理员将某在线用户停用后，该用户携带原有 accessToken 请求
- **THEN** 返回 401；即使持有 refreshToken 调用刷新接口，也无法换发新 accessToken

#### Scenario: 历史 token 兼容

- **WHEN** 升级部署前签发、载荷不含 ver 声明的 accessToken 访问接口，且该用户版本号仍为 0（未被任何动作递增过）
- **THEN** 认证通过，无需强制重新登录

### Requirement: 在线会话登记

系统 MUST 在登录成功时于 Redis 登记在线会话（键为用户 id，值含用户名、登录时间、登录 IP、token 版本），并设置与 token 生命周期匹配的过期时间；用户登出 MUST 移除本人会话；token 版本号递增的各类动作 MUST 同步移除该用户在线会话（除本人改密重登场景外不保留旧会话）。

#### Scenario: 登录登记会话

- **WHEN** 用户登录成功
- **THEN** Redis 中出现该用户的在线会话记录，含登录时间与 IP；重复登录（同账号再登录）覆盖旧记录，同一用户仅保留一条在线会话

#### Scenario: 登出移除会话

- **WHEN** 用户调用登出接口
- **THEN** 该用户的在线会话记录被移除

#### Scenario: 强制下线移除会话

- **WHEN** 管理员对某在线用户执行强制下线
- **THEN** 该用户版本号 +1 且在线会话记录被移除，其已签发 token 全部失效
