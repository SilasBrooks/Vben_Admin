# Design

## Context

- `JwtAuthFilter`（`service/src/main/java/com/vben/service/security/JwtAuthFilter.java:104`）每请求调用 `SysPermissionService.loadLoginUser(userId)`：3 条 SQL（用户、角色键、四表 JOIN 菜单权限）
- 权限数据变更源全部收敛在 `system` 模块三个 AdminService：用户（分配角色/禁用/重置密码，已有 `tokenVersionService.bump`）、角色（分配菜单/状态）、菜单（增删改/状态）
- 既有 Redis 约定：key 集中在 `RedisKeys`，安全判定（tokenVersion）fail-closed；项目单实例部署（docker-compose），无多实例广播问题
- 前端 nginx 镜像为官方 `nginx:stable-alpine`：支持 `gzip_static` 模块，不支持 brotli

## Goals / Non-Goals

**Goals**

- 每请求权限装配从 3 条 SQL 降为 0（命中时），失效语义不劣于现状
- 日志表常用查询列有索引
- 生产产物预压缩托管，传输体积显著下降

**Non-Goals**

- 不做分布式部署下的多实例缓存广播（单实例，TTL 兜底足够）
- 不缓存 `sys_user` 明细、字典、菜单树等其他热点（本次只动认证热路径）
- 不重构前端分包（vxe-table/dashboard/basic 大 chunk 为路由级分包，首屏不含；visualizer 产出的 stats.html 留作后续决策依据）
- 不改 tokenVersion 机制与在线会话逻辑

## Decisions

### D1：缓存内容与形态

缓存 `LoginUser` 的权限快照（username、roles、permissions 集合），JSON 序列化存 `vben:perms:{userId}`，TTL 5 分钟。不缓存：

- `status` 校验结果——`findActiveUserById` 语义是「禁用即 null」；禁用用户在 `SysUserAdminService` 已 bump tokenVersion，旧 token 在版本比对处被拒，到权限装配步的都是合法用户；缓存 miss 回源时仍执行 status 校验，语义不变
- 空值——不缓存 null，避免禁用复活类边界复杂化

### D2：失效策略三级收敛（核心正确性设计）

| 变更 | 失效动作 | 接线点 |
|------|---------|--------|
| 用户分配角色 / 禁用 / 重置密码 | `evict(userId)` | `SysUserAdminService` 两处既有 `tokenVersionService.bump` 旁 |
| 角色改状态 / 角色重分配菜单 | 查 `sys_user_role` 得该角色全部 userId，逐个 `evict` | `SysRoleAdminService` |
| 菜单增删改 / 状态切换 | 全量清理：`SCAN` 匹配 `vben:perms:*` 逐个 DEL | `SysMenuAdminService` 写方法 |

- 全量清理用 SCAN 而非 KEYS（生产 Redis 阻塞风险）；菜单变更低频，可接受
- TTL 5 分钟兜底覆盖漏接线的路径：最坏情况权限变更延迟 ≤5min 生效；已知的显式失效点全部即时生效
- 用户自身改资料（昵称/头像/改密）不动权限 → 不失效（改密已有 tokenVersion bump 兜旧 token 401）

### D3：降级语义——读路径可降级，判定路径不降级

- 权限缓存读/写包裹 try-catch：Redis 异常时记 warn 并**直查库**（与现状完全等价，无安全回退——缓存只是加速）
- `tokenVersionService.current` 的 fail-closed 逻辑**保持原样**（安全判定不受本次改动影响）
- 此点与 AGENTS「分布式状态必须走 Redis（fail-fast，不降级）」不冲突：该约定针对登录安全/在线会话/幂等等一致性状态；权限快照是只读派生数据，回源即真相

### D4：过滤器接入顺序

```
parse token → tokenVersion 比对（fail-closed，原样）
  → 读权限缓存 → 命中：构造 Authentication
  → miss/Redis 异常：loadLoginUser 回源 → 成功则异步回填缓存（同步 set 亦可，写入轻量）
  → user == null：401（原样）
```

`AuthController` 的 `/me` 类接口复用 `loadLoginUser` 之处不改（低频，且直查库保证强一致）。

### D5：日志表索引最小集

- `sys_login_log(username)`：登录日志按用户名筛选的高频查询
- `sys_oper_log(oper_user_id)`：操作日志按操作人筛选
- 不加 `status`/`module` 等低选择性列索引（小基数无收益）；时间列已有索引

`schema-postgres.sql` 更新建表语句内索引定义；开发存量库通过 `docker exec vben5 psql` 执行 `CREATE INDEX IF NOT EXISTS`（幂等，不锁业务小表）。

### D6：前端预压缩配置

- `.env.production`：`VITE_COMPRESS=gzip,brotli`（vben vite-config 内置 vite-plugin-compression，产出 `.gz` + `.br`）
- `nginx.docker.conf`：`gzip_static on`（官方镜像自带模块，直发 `.gz` 免实时压缩）+ `gzip_vary on`；`.br` 文件保留在镜像内暂不启用（官方 nginx 无 brotli 模块，后续换镜像/加模块时可用）
- 本地 `scripts/deploy/nginx.conf` 为官方 playground 模板遗留，与本项目部署无关，不改

## Risks / Trade-offs

- 角色授权变更后，未接线路径最坏 5min 延迟（TTL 兜底）；已识别变更点全部即时失效，风险低
- SCAN 全量清理在 perms key 极多时有扫描开销——实际用户量级（管理后台）远低于阈值
- 权限缓存引入后，权限相关集成测试可能依赖实时查库语义 → 失效接线必须有单测覆盖（变更即失效），必要时测试中显式 evict
- `gzip_static` 要求 `.gz` 与源文件同目录同名——vite-plugin-compression 默认就地产出，Dockerfile 不需改动；构建后需在 CI/本地验证 dist 中存在 `.gz`
