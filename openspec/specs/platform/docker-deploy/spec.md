# platform/docker-deploy Specification

## Purpose
提供 Docker 一键部署能力：一条 `docker compose up -d` 命令启动数据库、缓存、后端与前端完整演示环境，无需本地安装 JDK/Node/Maven 即可访问并登录系统。

## Requirements

### Requirement: 一键启动完整环境
仓库根目录的 compose 编排 SHALL 定义 postgres、redis、backend、frontend 四个服务，用户执行单条启动命令后 MUST 能通过浏览器访问前端页面并用种子账号（vben/123456）完成登录。

#### Scenario: 全新机器一键启动
- **WHEN** 在仅安装 Docker 的机器上于仓库根目录执行 compose 启动命令并等待构建完成
- **THEN** 四个服务全部运行，浏览器访问前端端口呈现登录页，使用 vben/123456 登录成功并进入工作台

#### Scenario: 后端经前端同源访问
- **WHEN** 浏览器向前端端口的 `/api/**` 路径发起请求
- **THEN** 前端容器将请求反代至后端服务，返回与直连后端一致的业务响应

### Requirement: 服务健康检查与启动依赖
compose 编排 SHALL 为 postgres、redis、backend 定义健康检查，backend MUST 在 postgres 与 redis 均健康后才启动；frontend MUST 在 backend 健康后才启动。

#### Scenario: 首次启动等待依赖就绪
- **WHEN** 首次执行 compose 启动命令（数据库为空卷）
- **THEN** 后端在 postgres/redis 健康检查通过后才开始启动，启动过程无「连接被拒绝」导致的退出重启

### Requirement: docker 运行 profile 行为
容器内后端 SHALL 使用专用运行 profile，该 profile MUST：数据源与 Redis 地址默认指向 compose 内部服务名且可被环境变量覆盖；启动时幂等执行建表与种子数据；验证码回显、MyBatis SQL 控制台日志、API 文档端点全部关闭；JWT 密钥 MUST 支持通过环境变量注入。

#### Scenario: 首次启动自动初始化数据
- **WHEN** postgres 数据卷为空时后端首次启动
- **THEN** 表结构与种子账号自动创建完成，vben/123456 可直接登录

#### Scenario: 重复重启不重复造数
- **WHEN** 已有数据的卷上重启后端容器
- **THEN** 幂等初始化不产生重复数据或启动报错

#### Scenario: 生产安全开关默认关闭
- **WHEN** 后端以 docker profile 运行
- **THEN** 验证码接口不回显明文、后端日志不打印 SQL、API 文档端点不可访问

### Requirement: 前端网关能力
前端容器 SHALL 通过 nginx 提供静态资源托管与 SPA 路由回退，并 MUST 满足：`/api` 反代透传 Cookie 与 Authorization 头；请求体上限不小于 10MB（支持文件上传）；AI 助手 SSE 流式响应不被缓冲（逐步可见而非一次性返回）。

#### Scenario: 头像直链可显示
- **WHEN** 用户在容器化环境中上传头像后刷新页面
- **THEN** 头像图片经 `/api` 反代正常显示（GET /file/*/content 的 Cookie 回退认证可用）

#### Scenario: 文件上传不受体积拦截
- **WHEN** 上传一个 8MB 的合法文件
- **THEN** 上传成功，nginx 网关不因默认 1MB 限制返回 413

#### Scenario: AI 回复流式输出
- **WHEN** 用户在 AI 助手中提问且已配置大模型 Key
- **THEN** 回复内容逐步流式呈现，而非长时间空白后一次性出现

### Requirement: 与本地开发环境隔离
容器化部署 SHALL 使用与本地开发不冲突的宿主端口（前端 5888、后端 18080），postgres 与 redis MUST NOT 映射宿主端口；数据 MUST 存于 compose named volume，与现有开发容器（vben5 / vben-redis）及其数据完全隔离。

#### Scenario: 开发环境与部署环境并存
- **WHEN** 本地开发环境（5777 前端 + vben5 + vben-redis）正在运行时执行 compose 启动命令
- **THEN** 两套环境互不影响，容器化环境通过 5888 端口正常访问

#### Scenario: 数据跨重启持久
- **WHEN** 在容器化环境中上传文件或修改数据后执行 compose down 并再次 up
- **THEN** 已上传文件与业务数据在重启后仍然存在
