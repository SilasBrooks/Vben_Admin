# Design: add-engineering-hardening

## Context

三块能力各有现成内建范式：幂等对齐 `common/ratelimit/` 的「注解 + AOP + Redis Lua」模式；MinIO 对齐 `common/storage/` 的可插拔接口与 `LocalStorageService` 实现形态；CI 依赖仓库现有目录结构（`service/` Maven、`front/` pnpm workspace）。

## Goals / Non-Goals

- Goals：幂等窗口拒绝重复请求；MinIO 实现可一键切换并通过真实容器验证；CI 两条流水线稳定绿；头部假通知清理。
- Non-Goals：不引入消息队列/分布式锁等更大基础设施；不做前端防抖之外的 UI 层防重（后端幂等为准）；CI 不跑 E2E（docker 起服务依赖重，保持手工 E2E 流程）。

## Decisions

### D1 幂等：SETNX + EXPIRE 原子脚本，键 `vben:idempotent:{name}:{principal}`

- 完全对齐 RateLimitAspect：`DefaultRedisScript` 内 `SET key val NX EX ttl`，返回是否成功；失败抛 `BizException.conflict(message)`。
- principal 复用 RateLimitAspect 的解析思路：登录用户取 `LoginUserHolder`，未登录降级 IP（`IpUtil.getClientIp`）。
- 注解属性：`name()`（必填，标识业务动作）、`intervalSeconds()`（窗口，默认 10）、`message()`（默认「请勿重复提交，请稍后再试」）。
- key 归 RedisKeys 集中管理（`idempotent(name, principal)`）。
- 落点：`PATCH /user/profile`、`POST /file/upload`（两处都是双击高发写操作）。
- 409 语义：新增 `BizException.conflict()` 工厂（HTTP 409），与 tooManyRequests(429) 区分。

### D2 MinIO：同 key 格式、构造期建 MinioClient、异常转译 StorageException

- 依赖 `io.minio:minio:8.5.x`（传递 okhttp）。`MinioStorageService` `@ConditionalOnProperty(vben.file.storage=minio)`，构造时由 `StorageProperties.Minio`（endpoint/accessKey/secretKey/bucket）建 `MinioClient`。
- store：`putObject`（流式，已知 size 用 `ObjectWriteResponse` 语义即可，size 未知时 MinIO 允许 -1 由 SDK 分片），key 复用同款 `yyyy/MM/dd/{uuid}.{ext}` 生成逻辑（提取共用或复制小段，避免为两处强行抽公共类）。
- open：`getObject` 返回流；ErrorResponseException 且 code=NoSuchKey → 转译「文件不存在」。
- delete：`removeObject`，NoSuchKey 容错静默（幂等）。
- 启动时 ensureBucket（幂等，不存在则创建）；MinIO 不可达仅 log.warn 不阻断启动，首次读写时再暴露错误。

### D3 CI：两条独立 job，测试策略=无容器单测

- `.github/workflows/ci.yml`，`on: push + pull_request`。
- backend：`actions/setup-java@v4`（temurin 21）→ `mvn -q -f service/pom.xml test`。
- frontend：`pnpm/action-setup` + `actions/setup-node@v4`（node 20, cache pnpm）→ `pnpm install --frozen-lockfile`（front/）→ `pnpm --filter @vben/web-ele build`。
- 单测无容器依赖：`IdempotentAspectTest` 用 Mockito mock `StringRedisTemplate`（execute 返回 1/0 两分支 + principal 解析），`IpUtilTest` 用 spring-test 的 `MockHttpServletRequest` 验证 XFF 链/remoteAddr 解析。不引入 Testcontainers/H2（PG 方言不兼容 H2，集成层已有手工 E2E 兜底）。

### D4 通知清理：整块移除

- `basic.vue` 删 notifications 数组、showDot、handleNoticeClear/markRead/remove/handleMakeAll/viewAll/handleClick、`Notification`/`NotificationItem` import、`<template #notification>` 块；头部铃铛消失（后续做站内消息时再以真实数据接回）。

## Risks / Trade-offs

- MinIO SDK 体积传递 okhttp：仅 minio profile 生效时加载，默认 local 不触碰。
- 幂等误伤「合法快速连续修改」：窗口 10s 偏短且 409 文案明确，业务可按注解调整 intervalSeconds。
- CI 前端构建耗时较长（vben monorepo 依赖大）：用 pnpm 缓存；不并行构建其他模板应用。

## Migration Plan

无 schema 变更；local→minio 切换时历史本地文件不迁移（新上传走 MinIO，旧记录仍指向本地键会 404，README 注明切换需评估存量）。
