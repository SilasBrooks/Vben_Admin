## Why

当前验证码、登录失败锁定、限流三道防线均为应用内存态，重启即清空、多实例无法共享；同时 accessToken 一旦签发在有效期内无法作废——修改密码、禁用用户、管理员重置密码后旧 token 仍然可用，存在真实安全风险；管理员也无法查看当前有哪些用户在线或将其强制下线。引入 Redis 作为集中式状态存储，可以一次性解决以上三类问题。

## What Changes

- **接入 Redis 基础设施**：后端引入 `spring-boot-starter-data-redis`（Lettuce），新增本地 Redis 容器（docker run 指令 + README 说明），`application-dev.yml` 增加连接配置
- **三道防线迁移 Redis**：`CaptchaService`（验证码池）、`LoginAttemptService`（失败锁定）、`RateLimitAspect`（限流窗口）的状态存储从 JVM 内存改为 Redis（SET + TTL / INCR + EXPIRE），**对外方法签名不变**，调用方（AuthController、AI 模块等）零改动
- **token 版本号即时失效**：Redis 维护用户 tokenVersion；JWT access/refresh 载荷新增 `ver` 声明；`JwtAuthFilter` 校验载荷版本与 Redis 当前版本一致，不一致返回 401。以下动作触发版本 +1：修改自己的密码、管理员重置密码、禁用用户、强制下线
- **在线用户管理**：登录成功写入 Redis 在线会话记录（用户 id → 用户名/登录时间/IP/tokenVersion），登出与强制下线时移除；新增监控模块「在线用户」页面（vxe-table 列表 + 强制下线按钮），后端提供列表/强退接口，权限码 `Monitor:Online:List` / `Monitor:Online:Kick`，菜单 seeder 同步登记

## Capabilities

### New Capabilities

- `security/session-control`: token 版本号机制与在线会话登记——凭证变更/强制下线后旧 token 即时失效，在线会话全生命周期（登录登记、登出移除、强退移除）由 Redis 管理
- `monitor/online-user`: 在线用户管理页——分页列表（用户名/登录时间/IP）与强制下线操作，按钮级权限控制

### Modified Capabilities

- `security/login-guard`: 「单实例内存语义边界」Requirement 替换为「Redis 集中存储」——验证码、失败锁定、限流窗口状态 MUST 存于 Redis（带 TTL），服务重启状态不丢失，多实例天然共享；Redis 不可用时验证码/登录接口 MUST 快速失败

## Impact

- **后端**：`service/pom.xml`（新增 starter）、`application.yml`/`application-dev.yml`（Redis 连接 + 现有 vben.captcha 配置保留）、`common/ratelimit/RateLimitAspect`、`module/auth/CaptchaService`、`module/auth/LoginAttemptService`、`security/JwtTokenService`（载荷加 ver）、`security/JwtAuthFilter`（版本校验）、`module/auth/AuthController`（登录登记/登出移除/改密 bump）、`module/system/service/SysUserAdminService`（重置密码/禁用 bump）、新增 `security/TokenVersionService`、`security/OnlineSessionService`、`module/monitor/controller/MonitorOnlineController`、`bootstrap/DatabaseSeeder`（菜单/权限种子）
- **前端**：新增 `front/apps/web-ele/src/api/monitor/online.ts` 与 `views/monitor/online/index.vue`（仿 login-log 页面模式）
- **部署**：新增 Redis 依赖（本地 docker 容器 `vben-redis` :6379，无密码）；后端启动时 Redis 连不上将 fail-fast（与 PG 一致的预期行为）
- **兼容性**：历史已签发且无 `ver` 声明的 token 视为版本 0，与 Redis 初始版本 0 匹配，滚动升级无需强制重新登录
- **API 文档**：springdoc 新增「在线用户」分组（自动扫描）
