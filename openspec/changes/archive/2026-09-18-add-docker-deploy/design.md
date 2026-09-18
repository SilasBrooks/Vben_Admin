## Context

- 后端 `service/`（Spring Boot 3.5，JDK 21，`spring-boot-maven-plugin` 可打可执行 jar），`context-path=/api`；dev profile 连 `127.0.0.1:5444` PG（容器 vben5）与 `127.0.0.1:6379` Redis（容器 vben-redis），`sql.init mode=always` 且 schema/data 全幂等
- prod profile 是 MySQL 版且要求外部注入 JWT 密钥环境变量，不适用于本次容器化演示环境（数据库是 PostgreSQL）→ 需要新 profile 而非复用 prod
- 前端为 pnpm monorepo，业务应用 `front/apps/web-ele`（包名 `@vben/web-ele`），构建脚本 `pnpm run build:ele`；模板自带 `front/scripts/deploy/Dockerfile` 构建的是 playground 且 nginx 无 `/api` 反代，不可复用
- `.env.production` 的 `VITE_GLOB_API_URL=https://mock-napi.vben.pro/api` 是模板 mock 地址；`VITE_GLOB_*` 由构建期 `extra-app-config` 插件注入运行时全局配置，改 .env 即生效，无需改业务代码
- 本机 Docker Hub 直连超时，需镜像源（docker.1ms.run，本地已有 bitnami/postgresql:18 与 docker.1ms.run/library/redis:8.8.0 镜像）
- 现有开发容器占用宿主端口：5444（PG）、6379（Redis）、8080（后端 dev）、5777（前端 dev）；CORS 白名单仅放行 5777/5666，容器化后走 nginx 同源反代即可绕开 CORS，无需改后端 CORS 配置
- JWT refresh Cookie 走 `Lax + secure=false`（application.yml 默认），同源 http 反代下浏览器可正常携带；`GET /file/*/content` 的 Cookie 回退认证依赖反代透传 Cookie

## Goals / Non-Goals

**Goals:**
- 根目录一条 `docker compose up -d` 起全套（构建+启动），`http://localhost:5888` 可登录演示
- 后端/前端镜像可重复构建（多阶段、缓存友好），构建上下文最小化
- 容器化环境与本地开发环境（容器、端口、数据）完全隔离，可并存
- 数据（PG/Redis/上传文件）跨 compose 重启持久

**Non-Goals:**
- 不做镜像推送仓库/CI 流水线、K8s 编排、HTTPS/TLS 终结
- 不做多实例水平扩展验证（Redis 状态集中式已为此铺路，但本环境单实例）
- 不复用/迁移现有开发容器数据，不做数据导入导出工具
- 不改变任何业务代码与接口行为

## Decisions

1. **新建 `application-docker.yml` profile 而非复用 dev/prod 或纯环境变量覆盖**
   - dev 硬编码本机地址与开启回显/SQL 日志；prod 是 MySQL 且 `sql.init=never`。容器化语义（compose 服务名 + 自动建库 + 安全开关全关）独立成 profile 最清晰，也符合「每行配置有明确业务含义」的项目约定。
   - 环境变量仍可覆盖（`SPRING_DATA_REDIS_HOST` 等），profile 内默认值即 compose 拓扑，`docker compose run` 单独起后端也能工作。
2. **后端镜像：两段式 Dockerfile（Maven 构建 → JRE 运行），构建上下文 `service/`**
   - 先 `COPY pom.xml` 依赖层缓存，再 `COPY src` 打包；运行层用 `eclipse-temurin:21-jre` 体积最小。备选「本机 mvn package 后仅拷 jar」被否：要求本地有 Maven/JDK，违背一键部署初衷。
   - `service/.dockerignore` 排除 `target/`，避免本地构建产物污染上下文。
3. **前端镜像：构建上下文取 `front/`（monorepo 根），Dockerfile 置于 `front/apps/web-ele/Dockerfile`**
   - pnpm workspace 安装必须以 monorepo 根为上下文（lockfile + 各 workspace 包）；compose 中 `context: ./front`、`dockerfile: apps/web-ele/Dockerfile` 分离指定即可。复用已存在的 `front/.dockerignore`（已排除 node_modules/dist 等）。
   - 构建命令 `pnpm run build:ele`（turbo 自动先构建依赖包）；产物取 `apps/web-ele/dist`。备选「模板 Dockerfile 改产物路径」被否：它构建全部包且输出 playground，耦合模板用途。
   - pnpm 10 + Node 22：`corepack enable` 即按 packageManager 字段激活。
4. **前端 nginx 配置独立成 `front/apps/web-ele/nginx.docker.conf`，不改模板 `scripts/deploy/nginx.conf`**
   - 关键点：`/api` → `http://backend:8080/api` 反代（透传 Host/Cookie/Authorization）；`client_max_body_size 15m`（上传 10MB + 表单开销）；`location /api/ai/chat` 关 `proxy_buffering`（SSE 流式）；SPA 走 `try_files ... /index.html`（hash 路由模式，回退兜底即可）。
5. **端口与数据隔离：前端 5888、后端 18080:8080（便于宿主 curl/E2E 直连），PG/Redis 不映射宿主端口**
   - 5444/6379/8080/5777 已被开发环境占用；不映射 PG/Redis 端口可彻底避免误连与端口冲突。数据卷用 compose named volume（`vben-deploy-pg-data` / `vben-deploy-redis-data` / `vben-deploy-files`），与开发环境 bind 挂载的 `D:\vben-sqlstore` 互不干扰。
6. **镜像源：Dockerfile/镜像名直接写 docker.1ms.run 前缀，注释说明海外去前缀**
   - 本机 Docker Hub 直连必超时；PG/Redis 复用本地已有镜像名免拉取。备选「标准名 + build-arg 注入前缀」被否：默认值仍指向 Docker Hub，本机首次 up 必失败，直观性差。
7. **JWT 密钥经 compose environment 注入，演示默认值 + 注释提醒更换**
   - docker profile 用 `${VBEN_JWT_ACCESS_SECRET:...}` 形式给演示默认值，保证开箱即用；README 上线清单已提示生产必须更换。`DEEPSEEK_API_KEY` 透传（可选，未配置时 AI 助手提示未配置，不影响其他功能）。
8. **compose 健康检查与启动顺序**
   - postgres：`pg_isready`；redis：`redis-cli ping`；backend：容器内 `wget` 探测 `GET /api/auth/captcha`（公开端点，兼验 Web 栈与 Redis 可用）。`depends_on` 全部 `condition: service_healthy`。
9. **上传目录**：后端运行目录 `/app`，`vben.file.storage.local.base-path=./files` → 挂 named volume 到 `/app/files`。

## Risks / Trade-offs

- [首次构建前端 monorepo 耗时较长（全量 pnpm install + turbo build）] → Docker 层缓存：依赖清单先拷先装，代码变更时仅重跑构建层；README 说明首次构建需耐心
- [docker.1ms.run 镜像源可用性不保证长期] → README 注明可替换其他镜像源或去前缀走官方源
- [容器内构建依赖 corepack 下载 pnpm（走 npm 源）] → 构建层已设 CI 环境变量；若网络受限可在构建层加 npm 镜像环境变量（README 备注）
- [18080 直接暴露后端绕过 nginx 网关（无上传体积网关限制）] → 后端自身有 10MB multipart 上限与业务校验兜底；该端口定位为调试用，README 说明
- [演示默认 JWT 密钥公开在仓库中] → 与现状（application.yml 占位密钥）风险等同，README 安全红线已覆盖；compose 注释明示更换
- [`sql.init mode=always` 依赖 schema/data 幂等] → 已有保证（dev 长期验证），docker profile 沿用同一套 SQL

## Migration Plan

纯新增能力，无数据/接口迁移。回滚 = 删除新增文件 + 还原 `.env.production` 两行改动；compose down -v 可彻底清除容器化环境数据。

## Open Questions

（无）
