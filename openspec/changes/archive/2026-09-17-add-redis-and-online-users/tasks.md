## 1. Redis 基础设施

- [x] 1.1 `service/pom.xml` 增加 `spring-boot-starter-data-redis`，`mvn -q compile` 通过
- [x] 1.2 `application-dev.yml` 配置 `spring.data.redis`（127.0.0.1:6379），`application-prod.yml` 同步补占位；启动 Docker 容器 `vben-redis`（`docker run -d --name vben-redis -p 6379:6379 redis:7-alpine`），README「快速开始」补充该容器指令与注意事项
- [x] 1.3 新建 `common/redis/RedisKeys.java`（key 前缀与命名常量）与在线会话/版本号等 JSON 序列化辅助（如需），验证后端启动可连通 Redis（`RedisConnectionFactory` 健康注入）

## 2. 三道防线迁移 Redis（接口签名不变）

- [x] 2.1 `CaptchaService` 改 StringRedisTemplate：generate `SET ... EX ttl`（保留 MAX_POOL/echo/AWT 绘制逻辑），verify 用 DEL 返回值实现"校验即销毁"；删除 ConcurrentHashMap 池；更新类注释（移除单实例声明）。验证：curl 获取验证码 → 正确码登录成功 → 同码复用被拒
- [x] 2.2 `LoginAttemptService` 改 Redis INCR+EXPIRE（首次失败设窗口 TTL，成功登录 DEL 两维度 key，checkLocked 由计数与 TTL 反推剩余时间）。验证：X-Forwarded-For 虚拟 IP 造 5 次失败 → 第 6 次 429 且提示剩余分钟；成功登录清零
- [x] 2.3 `RateLimitAspect` 改 Redis 固定窗口（Lua/事务保证 INCR 与 EXPIRE 原子）；删除本地 Window 表。验证：login 接口 1 分钟内第 11 次请求 429，窗口过后恢复
- [x] 2.4 重启后端复测：重启后仍处于锁定的账号继续 429（状态不因重启丢失）

## 3. token 版本号即时失效

- [x] 3.1 新建 `security/TokenVersionService`（`vben:token:ver:{userId}`，get/bump，bump 同时 DEL 在线会话），版本读取异常时 fail-closed 拒绝
- [x] 3.2 `JwtTokenService` 生成/解析携带 `ver` 声明（历史无 ver 视为 0），调用方（AuthController 登录/refresh、SysPermissionService 相关签发点）传入当前版本
- [x] 3.3 `JwtAuthFilter` 校验载荷 ver == Redis 当前版本，不一致 401；refresh 路径同样校验。验证：改密后旧 accessToken 请求 401、新登录 token 正常
- [x] 3.4 接入 bump 点：`AuthController.changePassword`、`SysUserAdminService.resetPassword`、`SysUserAdminService.updateUser`（status 0→1 禁用时）。验证：管理员重置密码/禁用用户后，目标用户旧 token 401 且 refresh 无法换发

## 4. 在线会话与在线用户管理（后端）

- [x] 4.1 新建 `security/OnlineSessionService`：登录成功写 `vben:online:{userId}`（username/nickname/loginTime/ip/ver，TTL=access 有效期），登出 DEL；`KEYS vben:online:*` 读取列表（注释标注量大换 SCAN）。验证：登录后 Redis 出现会话键，登出后消失
- [x] 4.2 新建 `module/monitor/controller/MonitorOnlineController`：`GET /monitor/online/list`（`Monitor:Online:List`，username 模糊 + 手动分页）、`POST /monitor/online/{userId}/kick`（`Monitor:Online:Kick`，禁止自踢；bump 版本 + DEL 会话）；springdoc @Tag「在线用户」。验证：curl 带 super token 列表可见、kick 后目标用户 401、自踢返回 400、无权限账号 403
- [x] 4.3 `DatabaseSeeder` 注册「在线用户」菜单（`/monitor/online` → `/monitor/online/index`，序 3）与 `Monitor:Online:List`/`Monitor:Online:Kick` 权限码并加入 systemSet；清库重启种子生效

## 5. 前端在线用户页面

- [x] 5.1 新建 `front/apps/web-ele/src/api/monitor/online.ts`（列表/强退接口与类型）
- [x] 5.2 新建 `views/monitor/online/index.vue`：仿 login-log 的 useVbenVxeGrid 页面（列：用户名/昵称/登录时间/IP/操作；username 搜索；强制下线按钮 `v-access:code="'Monitor:Online:Kick'"` + ElMessageBox 确认）；`pnpm --filter @vben/web-ele exec vue-tsc --noEmit` 通过
- [x] 5.3 浏览器冒烟：vben 登录 → 系统监控出现「在线用户」→ 列表可见当前用户 → kick stockAdmin（另浏览器登录）→ 其后续请求 401 跳登录页；受限账号看不到菜单与按钮

## 6. 集成验证与收尾

- [x] 6.1 全链路 curl E2E：验证码登录（XFF 虚拟 IP）→ 失败锁定 429 → 限流 429 → 改密旧 token 401 → 强退目标 401；Swagger UI 出现「在线用户」分组
- [x] 6.2 `openspec validate add-redis-and-online-users --strict` 通过；更新根 README 功能清单/注意事项（Redis 依赖、token 版本失效说明）；归档变更到 `openspec/specs/` 并 commit + push（gy，再 gy:master fast-forward）
