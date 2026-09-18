# Proposal: add-engineering-hardening

## Why

项目作为全栈后台模板，防护与部署能力已较完整（限流、验证码、在线用户、Docker 部署），但缺少三块中大型团队的常见基础设施：接口防重复提交（幂等）、对象存储实现（文件存本地磁盘在多实例部署下不共享、易丢失）、CI 自动化验证（目前完全依赖手工验证）。同时头部通知铃铛展示模板遗留的硬编码假数据，演示时穿帮。

## What Changes

- 新增 `@Idempotent` 注解 + AOP 切面：基于 Redis SETNX+EXPIRE 原子脚本，按登录用户（未登录降级 IP）+ 注解 name 维度在指定时间窗内只放行一次请求，重复提交返回 409；落点 PATCH /user/profile、POST /file/upload
- 新增 MinIO 存储实现 `MinioStorageService`：与本地磁盘实现同构（同 key 格式、同 `StorageService` 接口），通过 `vben.file.storage=minio` 切换，默认仍为 local；本地起 MinIO 容器实测上传/下载/删除
- 新增 GitHub Actions CI（backend job：mvn test；frontend job：pnpm build web-ele），并补两个无容器依赖的后端单测（IdempotentAspectTest、IpUtilTest）作为 CI 用例基础
- 清理头部通知假数据：删除 basic.vue 中硬编码通知数组与通知面板挂载，头部不再显示通知铃铛

## Capabilities

### New Capabilities

- `security/idempotency`：防重复提交——幂等窗口内重复请求被拒绝（409），窗口结束自动放行；键维度为「注解 name + 登录用户 id（未登录降级 IP）」
- `platform/ci`：CI 流水线——push/PR 自动跑后端单测（mvn test）与前端构建（pnpm build），任一失败则标记失败

### Modified Capabilities

- `platform/file-storage`：存储实现从「仅 local」扩展为「local | minio」二选一（`vben.file.storage` 切换），MinIO 实现与本地实现保持相同 key 格式与语义（store/open/delete），delete 幂等容错

## Impact

- 后端：`service/pom.xml` 新增 `io.minio:minio` 依赖；新增 `common/idempotent/`、`common/storage/MinioStorageService.java`；`StorageProperties` 增加 minio 配置组；`RedisKeys` 增加 idempotent key 方法；`BizException` 增加 conflict 工厂；`FileController`、`UserController` 挂注解；新增 `service/src/test/java` 单测
- 配置：`application.yml` 增加幂等说明注释与 MinIO 配置段（默认注释，保持 local 默认）
- 前端：`front/apps/web-ele/src/layouts/basic.vue` 删除通知相关代码与模板块
- CI：新增 `.github/workflows/ci.yml`
- 不涉及数据库 schema 变更，不影响现有接口契约（幂等仅在重复窗口内拒绝重复请求）
