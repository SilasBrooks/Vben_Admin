## 1. 后端容器化

- [x] 1.1 新增 `service/.dockerignore`（排除 target/ 等），验证构建上下文不含本地构建产物
- [x] 1.2 新增 `service/Dockerfile`（多阶段：maven 依赖层缓存 → 打包 → eclipse-temurin:21-jre 运行），`docker build` 单独构建通过且容器可起
- [x] 1.3 新增 `service/src/main/resources/application-docker.yml`（PG/Redis 指向 compose 服务名 + 环境变量可覆盖、sql.init always、echo/SQL 日志/Swagger 关闭、JWT 密钥环境变量默认值），核对无 SQL stdout 与 springdoc 开启项

## 2. 前端容器化

- [x] 2.1 修改 `front/apps/web-ele/.env.production`：`VITE_GLOB_API_URL=/api`、`VITE_ARCHIVER=false`
- [x] 2.2 新增 `front/apps/web-ele/nginx.docker.conf`（SPA try_files、`/api` 反代 backend:8080、透传 Cookie/Authorization、`client_max_body_size 15m`、AI SSE 路径关 proxy_buffering）
- [x] 2.3 新增 `front/apps/web-ele/Dockerfile`（多阶段：node 22 + corepack pnpm + `pnpm run build:ele`，构建上下文为 front/ → nginx 托管 dist + 上述 conf），`docker build` 单独构建通过

## 3. compose 编排

- [x] 3.1 新增根目录 `docker-compose.yml`：postgres/redis（本地已有镜像名、healthcheck、named volume）、backend（depends_on service_healthy、环境变量注入、/app/files 卷、18080:8080）、frontend（5888:8080、depends_on backend）、compose 内部网络
- [x] 3.2 `docker compose config` 校验编排合法且变量注入正确

## 4. 一键启动验证

- [x] 4.1 首次 `docker compose up -d` 全链路：四服务全部 healthy，后端日志无连接失败，种子数据自动创建
- [x] 4.2 curl 断言（宿主）：5888 首页 200、5888/api/auth/captcha 返回 captchaId、18080 直连 captcha 正常、Swagger 端点 404（docker profile 已关）
- [x] 4.3 登录链路：经 5888 同源登录 vben/123456 拿到 token 并可调通 /auth/menu；无 token 访问受保护接口 401
- [x] 4.4 上传与头像：经 5888 上传 8MB 文件成功（无 413）、头像 `/file/{id}/content` Cookie 回退认证可显示图片
- [x] 4.5 AI SSE：配置 DEEPSEEK_API_KEY 后 AI 提问流式逐步返回（无 Key 则验证功能降级提示不报 502）
- [x] 4.6 持久化与隔离：compose down && up 后数据仍在（登录日志/上传文件）；开发环境 vben5、vben-redis、5777 dev 全程不受影响
- [x] 4.7 安全开关抽查：captcha 响应无 devCode 字段、后端日志无 SQL 语句

## 5. 文档与收尾

- [x] 5.1 README 快速开始新增「Docker 一键部署」小节（前置条件/启动命令/端口说明/镜像源备注/数据卷说明）
- [x] 5.2 README 上线清单修正：`application-prod.yml` 表述由 MySQL 改为与实际一致（prod 为 MySQL 预留、容器化演示走 docker profile PostgreSQL），核对安全红线条目覆盖 JWT 密钥更换
- [ ] 5.3 openspec validate --strict 通过、归档变更、commit/push（gy + master）
