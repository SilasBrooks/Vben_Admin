# design

## Context

- 一键部署已有 compose 编排（postgres/redis/backend/frontend + 健康检查 + named volume），CI 目前只跑测试不产镜像，部署机必须现场 `--build`。
- 后端 Dockerfile 已是「pom 缓存层 + 全量 package」两段式；运行层 alpine 自带 wget 供健康检查。
- CI runner 在海外，直连官方镜像源无阻；本地构建在国内，需保留 `docker.1ms.run/` 前缀。
- GHCR 镜像名必须全小写，仓库 owner 为 `SilasBrooks`（硬编码小写 `silasbrooks`）。

## Goals

- push 到 gy/master 即产出可部署镜像，部署机免构建
- 后端容器内存可预期、OOM 快速失败
- 后端镜像分层，日常改动只重建业务层
- 敏感默认值不出现在编排文件字面量中（保留演示默认值但可被环境变量覆盖）

## Non-Goals

- 不做 master 分支保护、不引入私有 registry 登录体系（GHCR 公开包即可）
- 不做镜像瘦身（jlink / GraalVM native）、不做 CDS/AOT 启动优化
- 不改 docker profile 运行时行为与数据初始化逻辑

## Decisions

- **D1 镜像发布位置**：复用现有 `ci.yml` 追加 `build-images` job（push gy/master 触发），不新建 workflow 文件。触发条件 `branches: [gy, master]`，测试 job 通过后构建（needs: [backend, frontend]）。权限块 `packages: write`。
- **D2 标签策略**：`latest`（部署机 pull 默认拿最新）+ `sha-<短哈希>`（可回滚）。docker/metadata-action 产 sha 标签。
- **D3 基础镜像前缀参数化**：`ARG BASE_IMAGE_PREFIX=docker.1ms.run/` 放 Dockerfile 首段，`FROM ${BASE_IMAGE_PREFIX}library/...`；CI `build-args: BASE_IMAGE_PREFIX=`（空）。默认值保证本地 `--build` 行为不变；compose build.args 透传 `${DOCKER_IMAGE_PREFIX:-docker.1ms.run/}` 供需要者覆盖。
- **D4 后端分层**：`layertools extract`（Boot 3.5 仍支持，输出 dependencies / spring-boot-loader / snapshot-dependencies / application 四层目录，结构稳定）。ENTRYPOINT 用 exec form `java <JVM参数> org.springframework.boot.loader.launch.JarLauncher`（Boot 3.2+ launch 包路径）。alpine 运行层不变（wget 健康检查依赖保留）。
- **D5 JVM 参数**：exec form 数组内直接写 `-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError`——75% 给堆，余量留给 metaspace/堆外，防拖垮同机 PG/Redis；OOM 直接退出由 restart 策略拉起，不假死。
- **D6 compose 双模式**：backend/frontend 保留 `build:` 段、`image:` 改为 `ghcr.io/silasbrooks/vben-admin-{backend,frontend}:latest`。免构建部署：`docker compose pull backend frontend && docker compose up -d`；本地构建：`docker compose up -d --build`（产物打 ghcr 标签，无副作用）。GHCR 新包默认 private，README 写明首次发布后手动改 public（仓库本身 public，无额外泄露面）。
- **D7 PG 密码**：`POSTGRES_PASSWORD` 环境变量覆盖、默认值保持 123456（与 DatabaseSeeder 种子账号文档一致，演示定位）。backend 数据源未直接引用该密码（docker profile 默认连 compose 内 postgres 信任网络），故只需改 postgres 服务自身。

## Risks

- layertools 已被官方标记 deprecated（3.3+，仍可用）：若未来升级 Boot 删除，需切换 `-Djarmode=tools`；本期不引入不确定的 tools 结构。
- JarLauncher 全限定类名随 Boot 大版本变更风险：锁定在 3.x 启动器路径（`org.springframework.boot.loader.launch.JarLauncher`），升级 Boot 时需回归验证容器启动。
- GHCR 包可见性是首次发布后的手动动作：README 明确列出步骤，遗漏时表现为 pull 401/403，报错指向明确。
- compose `image:` 改名后本地旧镜像 `vben-deploy-*:latest` 残留不再被引用：无害，README 提示可清理。
