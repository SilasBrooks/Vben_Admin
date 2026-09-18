# Tasks: add-engineering-hardening

## 1. 防重复提交 @Idempotent

- [x] 1.1 新增 `common/idempotent/Idempotent.java`（name/intervalSeconds=10/message 属性）与 `IdempotentAspect.java`（Lua SETNX+EXPIRE 原子脚本，principal=登录用户 id，未登录降级 IP，重复抛 409）
- [x] 1.2 `BizException` 增加 `conflict()` 409 工厂；`RedisKeys` 增加 `idempotent(name, principal)`
- [x] 1.3 `PATCH /user/profile` 与 `POST /file/upload` 挂 `@Idempotent`
- [x] 1.4 curl 验证：同用户连发两次 PATCH /user/profile，第一次 200、第二次 409；等窗口过后第三次 200

## 2. MinIO 存储实现

- [x] 2.1 `pom.xml` 加 `io.minio:minio:8.5.x`；`StorageProperties` 加 `Minio` 配置组（endpoint/accessKey/secretKey/bucket）
- [x] 2.2 新增 `MinioStorageService`：`@ConditionalOnProperty(vben.file.storage=minio)`，同 key 格式 yyyy/MM/dd/uuid.ext，open 对 NoSuchKey 报文件不存在，delete 幂等容错
- [x] 2.3 `application.yml` 加 minio 配置段（默认注释，storage 保持 local）
- [x] 2.4 本地起 MinIO 容器（镜像源）实测：切 minio 上传→下载内容一致→删除两次幂等→切回 local 不建连接
- [x] 2.5 README 存储小节补充 MinIO 配置方式与存量文件不自动迁移说明

## 3. CI 与单测

- [x] 3.1 补 `IdempotentAspectTest`（mock StringRedisTemplate：放行/拒绝/未登录降级 IP 三分支）
- [x] 3.2 补 `IpUtilTest`（MockHttpServletRequest：XFF 单值/多级链/无 XFF 回退 remoteAddr）
- [x] 3.3 本地 `mvn test` 全绿；新增 `.github/workflows/ci.yml`（backend：setup-java 21 + mvn test；frontend：pnpm + node 20 + `pnpm --filter @vben/web-ele build`，pnpm 缓存）

## 4. 通知假数据清理

- [x] 4.1 `basic.vue` 删除 notifications 数组、相关处理函数、Notification import 与 `<template #notification>` 块
- [x] 4.2 前端 typecheck 通过；浏览器确认头部铃铛消失、页面无控制台报错

## 5. 收尾

- [x] 5.1 dev 环境回归：登录/资料修改/上传不受影响（幂等仅拦窗口内重复）
- [x] 5.2 README 功能清单补「防重复提交」「MinIO 存储」「CI」
- [x] 5.3 `openspec validate --strict` 通过；归档与 commit/push 等用户确认后执行
