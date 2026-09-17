## Context

后端为单模块 Spring Boot 3.5（`service/`），认证为无状态 JWT 双 token（access 7d / refresh 30d httpOnly Cookie），`JwtAuthFilter` 白名单放行公开路径。三道防线（`CaptchaService`、`LoginAttemptService`、`RateLimitAspect`）现均为 JVM 内 ConcurrentHashMap + 惰性过期，接口签名稳定且调用方仅有 AuthController 与 AI 切面。权限体系：`@RequirePermission` 后端兜底 + 前端 `v-access:code`，菜单种子在 `DatabaseSeeder`。前端监控模块已有 `login-log`、`oper-log` 两个 vxe-table 页面可仿写。

## Goals / Non-Goals

**Goals:**

- Redis 成为后端集中式状态存储的第一站，为后续缓存/分布式能力铺路
- 三道防线对外行为（返回码、错误文案、检查顺序）完全不变
- 凭证变更与强制下线对已签发 token 即时生效
- 在线用户可视可管（列表 + 强退）

**Non-Goals:**

- 字典/权限/菜单等业务数据缓存到 Redis（明确后置）
- refresh token 本身的集中式白名单管理（仅随版本号机制间接失效）
- WebSocket 实时推送在线列表（轮询/手动刷新即可）
- 生产 Redis 高可用部署方案（仅提供本地容器与连接配置）

## Decisions

- **客户端选择：Spring Boot 默认 Lettuce + StringRedisTemplate**。不引入 Redisson（当前无分布式锁/布隆过滤器需求）；统一用 StringRedisTemplate 手写 JSON 序列化（在线会话值结构简单，避免 JdkSerialization 的 class 版本耦合）。key 统一前缀 `vben:`，便于排查与后续隔离
- **三服务迁移保持接口签名不变**：`CaptchaService`（`SET vben:captcha:{id} code EX ttl`，verify 用 GET+DEL 语义 → 用 `DELETE` 返回值判断存在性以实现"校验即销毁"）、`LoginAttemptService`（计数 `INCR` + 首次失败时 `EXPIRE` 窗口；`checkLocked` 读计数反推剩余时间）、`RateLimitAspect`（固定窗口 `INCR` + `EXPIRE`，窗口起点由 Redis TTL 反推，删除本地 Window 类）
- **token 版本号存 Redis 而非 DB**：`vben:token:ver:{userId}`，读多写少且允许"Redis 丢失后版本归零"（归零 = 存量 token 全部恢复有效，与旧世界行为一致，无安全放大）。JWT 载荷加 `ver` 声明；`JwtAuthFilter` 解析后与当前版本比对。`JwtTokenService` 生成与解析均带版本参数
- **版本递增点收敛到 `TokenVersionService.bump(userId)`**：AuthController 改密、SysUserAdminService 重置密码、SysUserAdminService 禁用用户（status 0→1 时）、MonitorOnlineController 强退 四处调用；bump 同时删除在线会话。禁用判断放在 service 层而非 controller，避免绕过
- **在线会话**：`vben:online:{userId}` → `{username, nickname, loginTime, ip, ver}` JSON，TTL 与 access token 有效期一致（7d）；登录成功写入（覆盖旧会话 → 天然"同账号单会话"），登出/强退 DEL。列表查询用 `KEYS vben:online:*`（当前规模可接受，量大再换 SCAN；在代码注释中注明）
- **Redis 不可用策略：fail-fast 不降级**。验证码/登录/限流路径上的 Redis 异常直接抛 500（全局异常处理器兜底），与"验证码校验不可跳过"的安全语义一致；仅 token 版本比对在 Redis 异常时选择拒绝请求（fail-closed），防止宕机窗口被利用
- **本地 Redis 容器**：`docker run -d --name vben-redis -p 6379:6379 redis:7-alpine`（无密码，仅本机），与 vben5 容器同样的使用方式写入 README；`application-dev.yml` 配 `spring.data.redis.host/port`，prod yml 同步补占位
- **菜单种子**：`DatabaseSeeder` 在 Monitor 目录下新增「在线用户」菜单（`/monitor/online`，icon `ant-design:team-outlined`，序 3）+ 两个 F 型权限码 `Monitor:Online:List`/`Monitor:Online:Kick`，加入 systemSet 授权 super/admin

## Risks / Trade-offs

- [新增强依赖：Redis 挂则验证码/登录不可用] → 与 PG 同级的"先起容器再起后端"预期，README 注意事项明示；启动期 Lettuce 连接失败即 fail-fast，避免半可用状态
- [KEYS 全量扫在线会话在用户量大时阻塞 Redis] → 当前单实例模板项目规模无虞；代码注释标注 SCAN 迁移点
- [版本号存 Redis，Redis 数据丢失会导致已"踢下线"用户 token 复活] → 语义等同回到旧版本（token 自然过期），可接受；README 注明不要对 vben 库执行 FLUSHALL 的运维约束
- [存量 token 无 ver 声明按版本 0 兼容] → 一次上线期窗口，若需立即全量下线可将所有用户版本预置 1（提供手动说明，不做自动化）
- [限流 INCR+EXPIRE 非原子可能残留无 TTL 键] → 使用 Lua 脚本（INCR 后按返回值==1 判定设置 EXPIRE）或 EXEC 事务，保证窗口必然过期

## Migration Plan

1. 起 Redis 容器 → 2. 后端加依赖与配置重启（此时旧内存态清空一次，属预期）→ 3. 存量已登录用户凭无 ver token 继续可用（版本 0 兼容）→ 4. 回滚：还原代码即可，Redis 容器可保留（无状态污染）

## Open Questions

- 无
