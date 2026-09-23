# docker-deploy-optimization

## Why

当前一键部署在部署机上现场构建前后端镜像：首次 20 分钟起步（全量 Maven/Node 编译），改一行代码也要重编全量依赖；后端容器裸 `java -jar` 启动无内存约束，宿主内存紧张时可能被 OOMKill 或拖垮同机 PG/Redis；PG 密码硬编码在 compose 文件里；Dockerfile 基础镜像前缀写死国内源，CI（海外 runner）无法直接复用。

## What Changes

- CI（GitHub Actions）在 push 到 gy/master 时构建后端、前端双镜像并发布到 GHCR（`latest` + `sha-<短哈希>` 双标签，buildx gha 缓存加速）——部署机 `docker compose pull && up -d` 免构建部署，分钟级完成
- 两个 Dockerfile 基础镜像前缀改为 `ARG BASE_IMAGE_PREFIX`（默认国内源 `docker.1ms.run/`，CI 传空走官方源），本地行为不变
- 后端镜像改为 Spring Boot layertools 分层结构（依赖层与业务层分离，改动代码只重建/重传业务层），ENTRYPOINT 增加 JVM 容器内存约束（`-XX:MaxRAMPercentage=75.0`、`-XX:+ExitOnOutOfMemoryError`）
- compose 中 PG 密码参数化 `${POSTGRES_PASSWORD:-123456}`；backend/frontend 的 `image` 指向 GHCR 正式名（保留 build 段，本地构建与远程拉取双模式并存）
- README 部署段同步：免构建拉取模式、GHCR 包可见性设置、安全参数说明

## Capabilities

- `platform/docker-deploy`（MODIFIED + ADDED）

## Impact

- `.github/workflows/ci.yml`：新增 build-images job
- `service/Dockerfile`、`front/apps/web-ele/Dockerfile`：前缀参数化、后端分层
- `docker-compose.yml`：密码参数化、镜像名
- `README.md`：部署文档同步
- 无 Java/Vue 业务代码变更，不影响本地开发链路与既有 CI 测试 job
