# platform/docker-deploy 变更

## MODIFIED Requirements

### Requirement: 一键启动完整环境
仓库根目录的 compose 编排 SHALL 定义 postgres、redis、backend、frontend 四个服务，用户执行单条启动命令后 MUST 能通过浏览器访问前端页面并用种子账号（vben/123456）完成登录。backend 与 frontend 服务 SHALL 同时声明 `build` 段与 GHCR 正式镜像名，支持「本地构建」与「拉取 CI 发布镜像」两种部署模式。postgres 服务密码 MUST 支持通过环境变量覆盖（默认保留演示值）。Dockerfile 基础镜像源前缀 MUST 支持构建参数覆盖（默认国内源 `docker.1ms.run/`）。

#### Scenario: 全新机器一键启动
- **WHEN** 在仅安装 Docker 的机器上于仓库根目录执行 compose 启动命令并等待构建完成
- **THEN** 四个服务全部运行，浏览器访问前端端口呈现登录页，使用 vben/123456 登录成功并进入工作台

#### Scenario: 后端经前端同源访问
- **WHEN** 浏览器向前端端口的 `/api/**` 路径发起请求
- **THEN** 前端容器将请求反代至后端服务，返回与直连后端一致的业务响应

#### Scenario: 免构建拉取部署
- **WHEN** 部署机执行 `docker compose pull backend frontend` 后执行 compose 启动命令（无本地构建环境与依赖缓存）
- **THEN** backend/frontend 镜像从 GHCR 拉取（latest 标签），四个服务全部运行且功能与本地构建版本一致

#### Scenario: 本地构建行为不变
- **WHEN** 在国内网络环境执行 `docker compose up -d --build` 且未传入基础镜像前缀覆盖变量
- **THEN** 构建使用默认国内镜像源前缀，产物以 GHCR 正式镜像名命名，行为与改造前一致

## ADDED Requirements

### Requirement: CI 构建并发布镜像
GitHub Actions SHALL 在 push 到 gy 或 master 分支且测试 job 全部通过后，构建后端与前端生产镜像并发布到 GHCR（`ghcr.io/silasbrooks/vben-admin-backend` 与 `ghcr.io/silasbrooks/vben-admin-frontend`），MUST 同时打 `latest` 与 `sha-<短哈希>` 标签，MUST 在海外 runner 环境以官方源构建（基础镜像前缀构建参数传空），SHALL 使用 GitHub Actions 缓存加速构建。

#### Scenario: push 触发镜像发布
- **WHEN** 向 gy 分支推送提交且 backend/frontend 测试 job 均通过
- **THEN** build-images job 构建双镜像并推送到 GHCR，产出 latest 与 sha-<短哈希> 双标签

#### Scenario: 镜像可回滚
- **WHEN** 部署机需要回退到历史版本
- **THEN** 以对应提交的 `sha-<短哈希>` 标签镜像重新启动容器即可

### Requirement: 后端容器资源约束与分层镜像
后端镜像 SHALL 采用 Spring Boot layertools 分层结构（依赖层与业务层分离），ENTRYPOINT MUST 以 exec form 启动并附 JVM 容器内存约束：最大堆为容器内存限额的 75%（`-XX:MaxRAMPercentage=75.0`），发生堆内存溢出 MUST 立即退出进程（`-XX:+ExitOnOutOfMemoryError`）由容器重启策略接管。运行层 MUST 保留 alpine 基础镜像（健康检查依赖内置 wget）。

#### Scenario: 后端容器内存可预期
- **WHEN** 后端容器被赋予固定内存限额运行并施加载荷
- **THEN** JVM 最大堆不超过限额的 75%，不因堆外膨胀导致宿主机其他容器（postgres/redis）被挤压

#### Scenario: 堆溢出快速失败
- **WHEN** 后端发生堆内存溢出
- **THEN** 进程立即退出并触发容器重启，不长时间假死占用资源

#### Scenario: 日常改动重建只触及业务层
- **WHEN** 仅修改后端业务代码（依赖未变）重新构建镜像
- **THEN** dependencies 等依赖层命中构建缓存，仅 application 层重建，镜像推送增量仅业务层体积
