## Why

调查确认了两处随数据量/流量增长会恶化的性能瓶颈与一处部署交付浪费：

1. **认证过滤器每请求 3 条 SQL**：`JwtAuthFilter` 对每个携带 token 的请求调用 `SysPermissionService.loadLoginUser`，执行 `sys_user` 单查、`sys_user_role` JOIN `sys_role` 查角色键、四表 JOIN `selectMenusByUserId` 查权限码——权限数据仅在角色/菜单/用户授权变更时才变化，却以每请求全量查库的方式装配，是全站接口的公共开销
2. **日志表查询缺索引**：`sys_login_log` 按 `username` 查询（登录日志分页）、`sys_oper_log` 按 `oper_user_id` 查询均走顺序扫描，数据量增长后管理端日志页变慢
3. **前端产物未预压缩**：`.env.production` `VITE_COMPRESS=none`，nginx 容器仅动态 gzip（每请求实时压缩耗 CPU）；主 chunk（vxe-table 741KB / dashboard 663KB / basic 535KB）均为路由级分包、首屏不含，本变更不做分包重构，仅启用预压缩静态托管把传输体积降 60%+

## What Changes

- 后端权限快照缓存：`LoginUser`（角色 + 权限码）按 `userId` 缓存至 Redis（key `vben:perms:{userId}`，TTL 5 分钟兜底）；`JwtAuthFilter` 命中缓存跳过 3 条 SQL，miss 回源查库并回填
- 三级主动失效：用户级变更（分配角色/禁用/重置密码等既有 tokenVersion bump 点）失效单用户；角色级变更（角色菜单授权/角色状态）失效该角色下全部用户；菜单级变更（菜单增删改/状态）全量失效（SCAN 清理）
- 读取路径降级语义：Redis 异常时权限装配直查库（与现状等价，缓存仅为加速）；tokenVersion 校验维持 fail-closed 不变
- 日志表索引：`sys_login_log(username)`、`sys_oper_log(oper_user_id)`，`schema-postgres.sql` 更新 + 开发存量库手动 SQL 同步
- 前端预压缩：`.env.production` 开启 `VITE_COMPRESS=gzip,brotli`，`nginx.docker.conf` 启用 `gzip_static on` 与 `gzip_vary on`
- 产物体积分析：用 `build:analyze`（visualizer）产出 stats.html 供后续分包决策（任务项，无 delta）
- 无 API 协议、权限码、菜单变更

## Capabilities

### New Capabilities

- `system/permission-cache`: 认证过滤器权限快照缓存——读取命中、三级失效、降级语义、安全边界（tokenVersion 不受影响）

### Modified Capabilities

- `platform/docker-deploy`: 「前端网关能力」增加静态资源预压缩托管要求——构建产物附 `.gz`/`.br`，nginx 优先直发预压缩文件

## Impact

- 后端：新增 `common/redis/RedisKeys.perms`、`system/service/PermissionCacheService`（或等价位置）；修改 `JwtAuthFilter`、`SysPermissionService`（回源复用）、`SysUserAdminService` / `SysRoleAdminService` / `SysMenuAdminService`（失效接线）
- 数据库：`schema-postgres.sql` 两个索引；开发存量库（vben5 容器）手动执行同步 SQL
- 前端：`apps/web-ele/.env.production`、`nginx.docker.conf` 两处配置；无代码逻辑变更
- 测试：权限缓存命中/失效/降级单测；现有认证/权限测试回归
