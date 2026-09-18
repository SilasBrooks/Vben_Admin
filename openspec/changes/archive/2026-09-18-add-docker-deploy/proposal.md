## Why

项目目前只能通过「手动起 Docker 容器 + mvn + pnpm dev 三步」跑起来，演示与交付成本高。提供一套 `docker compose up -d` 一键启动的完整环境（PG + Redis + 后端 + 前端 nginx），让任何人拉下仓库后一条命令即可获得可登录演示的全套系统。

## What Changes

- 新增根目录 `docker-compose.yml`：编排 postgres / redis / backend / frontend 四服务，含健康检查、启动依赖与 named volume 数据持久化
- 新增 `service/Dockerfile`（多阶段：Maven 构建 jar → JRE 21 运行）与 `service/.dockerignore`
- 新增 `front/apps/web-ele/Dockerfile`（多阶段：Node 22 + pnpm 构建 web-ele → nginx 托管）与 `front/apps/web-ele/nginx.docker.conf`（静态托管 + `/api` 反代 + 上传体积限制 + SSE 关缓冲）
- 新增后端 `application-docker.yml` profile：数据源/Redis 指向 compose 服务名（环境变量可覆盖），幂等自动建库，关闭验证码回显/SQL 日志/API 文档
- 修改 `front/apps/web-ele/.env.production`：`VITE_GLOB_API_URL` 由模板 mock 地址改为 `/api`（同源反代），关闭构建产物 dist.zip 打包
- 更新 README：快速开始新增「Docker 一键部署」小节
- 端口规划与本地开发环境完全隔离：前端 5888、后端 18080（调试用）、PG/Redis 不映射宿主端口，不复用现有 vben5 / vben-redis 开发容器及其数据

## Capabilities

### New Capabilities
- `platform/docker-deploy`: Docker 一键部署能力——compose 编排（四服务拓扑、健康检查、启动依赖）、后端/前端镜像构建约定、docker 运行 profile 行为（自动初始化、安全开关默认关闭）、端口与数据卷隔离约定

### Modified Capabilities

## Impact

- 新增文件：`docker-compose.yml`、`service/Dockerfile`、`service/.dockerignore`、`front/apps/web-ele/Dockerfile`、`front/apps/web-ele/nginx.docker.conf`、`service/src/main/resources/application-docker.yml`
- 修改文件：`front/apps/web-ele/.env.production`（API 地址）、`README.md`（快速开始）
- 不改动任何业务代码与既有接口行为；本地开发流程（dev profile + vben5/vben-redis 容器）不受影响
- 依赖：Docker Engine / Docker Compose v2；构建期基础镜像使用国内镜像源前缀（docker.1ms.run），海外环境可去除
