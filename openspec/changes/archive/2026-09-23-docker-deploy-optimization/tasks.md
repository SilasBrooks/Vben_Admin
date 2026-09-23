# tasks

- [x] 1. 双端 Dockerfile 基础镜像前缀参数化（`ARG BASE_IMAGE_PREFIX`，默认国内源；compose build.args 透传）
- [x] 2. 后端 Dockerfile layertools 分层 + JVM 内存约束（MaxRAMPercentage=75.0 / ExitOnOutOfMemoryError，JarLauncher 启动）
- [x] 3. compose：PG 密码参数化 `${POSTGRES_PASSWORD:-123456}`；backend/frontend image 指 GHCR 正式名（保留 build 段）
- [x] 4. CI 追加 build-images job：needs 测试双 job、packages:write、login-action + buildx（gha 缓存）、双镜像 latest+sha 标签、BASE_IMAGE_PREFIX 传空
- [x] 5. 本地验证：`docker compose build` 成功，后端容器启动健康检查通过，登录页可访问且 vben/123456 登录成功
- [x] 6. README 部署段同步：免构建 pull 部署模式、GHCR 包改 public 步骤、PG 密码与 JVM 参数说明、旧镜像清理提示
- [ ] 7. 归档 openspec 变更（sync delta → archive）
