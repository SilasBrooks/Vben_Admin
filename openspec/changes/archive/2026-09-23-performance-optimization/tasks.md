# Tasks

## 后端：权限快照缓存

- [x] 1. `RedisKeys` 新增 `perms(Long userId)` 与前缀常量；新建 `PermissionCacheService`：get（JSON 反序列化 LoginUser）/ put（TTL 5min）/ evict / evictByRole（查 sys_user_role 逐个失效）/ evictAll（SCAN `vben:perms:*`）；Redis 异常 try-catch 降级（warn 日志 + 返回 miss）
- [x] 2. `JwtAuthFilter` 接入：tokenVersion 比对后优先读缓存，miss 走 `loadLoginUser` 回源并回填；`user == null` 401 语义不变
- [x] 3. 失效接线：`SysUserAdminService` 两处 bump 旁 `evict(userId)`；`SysRoleAdminService` 角色分配菜单/状态变更 `evictByRole`；`SysMenuAdminService` 菜单写操作 `evictAll`
- [x] 4. 单测：缓存命中不再查库（mock mapper 验证）、用户/角色/菜单三级失效各自生效、Redis 异常降级直查库；`mvn test` 全量回归

## 数据库：日志表索引

- [x] 5. `schema-postgres.sql`：`sys_login_log` 加 `idx_login_log_username(username)`、`sys_oper_log` 加 `idx_oper_log_user(oper_user_id)`
- [x] 6. 开发存量库手动同步：`docker exec vben5 psql` 执行 `CREATE INDEX IF NOT EXISTS`（与 schema 两处一致）

## 前端：预压缩托管

- [x] 7. `apps/web-ele/.env.production`：`VITE_COMPRESS=gzip,brotli`
- [x] 8. `apps/web-ele/nginx.docker.conf`：`gzip_static on`、`gzip_vary on`，核对既有 gzip 参数
- [x] 9. `pnpm build:ele` 确认 dist 附带 `.gz`/`.br` 产物；`pnpm check:type` 通过

## 收尾

- [x] 10. `build:analyze` 产出 visualizer stats.html（`apps/web-ele/node_modules/.cache/visualizer/`，本地分析用，不入 git），大 chunk 构成结论记入归档备注
  - 结论：vxe-table 741KB / dashboard 663KB（echarts 已按需引入 echarts/core）/ basic 537KB / index 入口 235KB——大 chunk 均为路由级/布局级分包，首屏不含，懒加载可达；按 design NonGoal 不重构分包，以 .gz/.br 预压缩降低传输体积
- [x] 11. 同步根 README.md「注意事项/项目亮点」（权限缓存 + 预压缩部署）
- [x] 12. 归档 openspec 变更（sync delta → archive）
