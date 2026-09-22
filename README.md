# Vben Admin 管理系统

> 企业级中后台管理系统：**Spring Boot 3 (JDK 21) + MyBatis-Plus + PostgreSQL** 后端，**Vue 3 + Vite + Element Plus（Vben Admin 5.7）** 前端，内置**按钮级 RBAC 权限体系**与 **AI 智能助手（可插拔 LLM Agent 编排）**。
>
> 前后端完全分离、服务端无状态（JWT 双 Token），开箱即可对接 Vben Admin 前端，也可作为新项目的权限底座二次开发。

---

## 项目亮点

- **按钮级 RBAC + 动态路由**：菜单驱动前端路由生成，接口层自定义拦截器按权限码（如 `System:User:Add`）校验，前端按钮用 `v-access:code` 指令同步显隐，前后端权限一码贯通
- **JWT 双 Token + token 版本号即时失效**：accessToken 走响应体、refreshToken 走 httpOnly Cookie；改密/重置密码/禁用用户/强制下线通过 Redis 中的版本号让已签发 token 立即作废，兼顾无状态水平扩展与"服务端可控失效"
- **数据权限（行级）**：`@DataScope` 注解 + AOP 切面 + ThreadLocal 上下文，按部门/角色动态改写 SQL 过滤范围，支持"仅本人/本部门/本部门及以下/自定义"多种粒度
- **AI 智能助手**：DeepSeek 流式 SSE + 工具调用（Function Call）Agent 编排——查询类工具自动执行回喂，写操作类工具生成确认卡片、用户确认后才落库；服务端按权限码双重校验；会话支持按轮截断 + 滚动摘要 + 本地持久化
- **声明式操作审计**：`@OperLog` 注解 + 切面自动记录操作人/入参/结果/耗时/IP，密码字段自动脱敏，异步落库不影响主流程
- **工程化变更管理**：使用 [OpenSpec](openspec/changes/archive/) 规范驱动开发，每个功能有 proposal / design / spec / tasks 四件套并归档可追溯

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21、Spring Boot 3.5.6、MyBatis-Plus 3.5.12、JJWT 0.12.6、spring-security-crypto（BCrypt） |
| 数据库 | PostgreSQL 18（开发，Docker）、MySQL（生产就绪，`schema-mysql.sql`） |
| 缓存/会话 | Redis 7+（Docker）：验证码、登录锁定、限流窗口、token 版本号、在线会话（集中式状态，重启不丢、多实例共享） |
| 前端 | Vue 3、Vite、Element Plus、Pinia、Vue Router、Vben Admin 5.7 monorepo（pnpm + turbo） |
| AI | LLM 可插拔：模型配置页管理多厂商（OpenAI 兼容协议，SSE 流式 + Function Call），全局唯一激活；yml `DEEPSEEK_API_KEY` 兜底 |

## 功能清单

| 模块 | 内容 |
|---|---|
| 认证 | 登录 / 双 Token 刷新 / 登出 / BCrypt 密码加密 / 凭证变更与强退后旧 token 即时失效（版本号机制） |
| 登录安全 | 服务端图形验证码（一次性 + 2 分钟有效期）、失败锁定（用户名/IP 双维度 15 分钟 5 次）、声明式接口限流 `@RateLimit`——状态存 Redis，重启不丢、多实例共享 |
| 防重复提交 | `@Idempotent` 注解 + Redis SETNX 原子占位：同用户在时间窗内重复提交返回 409（未登录降级 IP 维度），资料修改、文件上传等写接口已接入 |
| 系统管理 | 用户、角色、菜单、部门管理（CRUD + 树形结构 + 分页） |
| 权限 | 按钮级 RBAC 权限码、菜单驱动动态路由、角色授权 |
| 数据权限 | 部门粒度行级数据隔离（stockAdmin 角色为演示账号） |
| 监控 | 操作日志、登录日志（声明式采集）、在线用户（活跃会话列表：30 分钟滑动窗口，每次请求自动续期——登出/强制下线/改密/长时间无操作即从列表消失，ver 失配残留自愈；强制下线即时踢出且禁止对自己执行） |
| 数据字典 | 可维护字典 + 前端 `useDict` hook（自动缓存共享） |
| 文件存储 | 可插拔存储抽象（`StorageService`）：本地磁盘 / MinIO 对象存储（`vben.file.storage=minio` 切换，启动自动建桶，对象存储解决多实例文件不共享与单机丢失）、扩展名白名单 + 10MB 上限 + UUID 随机存储名、文件管理页（列表/上传/预览/删除）、头像上传接入 |
| 仪表盘 | 工作台/分析页真实数据版（`GET /dashboard/summary` 登录即可全员同版）：统计卡 + 今日概况 + 近 14 天登录趋势 + 部门/模块分布 + 最近登录/操作，替代模板演示数据 |
| 个人中心 | 头像上传（登录即可，同步 header）、昵称/个人简介编辑（`PATCH /user/profile` 登录即可，仅限本人）、修改密码（成功后强制重新登录，旧 token 即时失效） |
| 站内通知 | 顶栏铃铛统一聚合三类消息（微信式交互）：**聊天消息**（IM 未读会话，点击直达 `/im?peer=` 定位会话）+ **公告/通知**（`sys_notice`，公告跳消息中心公告筛选、通知点击已读），红点 = IM 未读 + 通知未读，hover 浮层预览、铃铛本体不跳转；安全事件（强退/密码重置/停用/授权变更）自动通知当事人；页面操作后经 `notice:refresh` 事件同步铃铛（`/api/notice/*` 登录即可、支持 msgType 过滤；`/api/ws/notice`、`/api/ws/im` 双通道实时推送，handler 按 userId 多连接推送互不顶替） |
| 公告广播 | 管理员发布页（系统管理 → 公告发布，super/admin 可见）：按全员/部门（部门树多选）/指定用户（搜索多选）三粒度发布，复用站内通知通道落库 + 铃铛实时推送（`msg_type=announcement`，`POST /notice/announce` 挂 `Notice:Announce:Publish` 权限码 + 幂等防重，仅发送启用账号） |
| 消息聊天（IM 单聊） | 用户间一对一私聊（`sys_message` 落库可回溯，离线重登可见）：联系人选择、会话列表（未读数）、历史消息（游标分页）、发送与已读回执（`/api/im/*` 登录即可，仅操作本人会话，发送按用户限流 60 次/分钟）；`/api/ws/im?token=` 实时推送，对方在线即时送达并回已读回执；独立「消息聊天」页全员可见；微信式消息右键菜单（引用 / 复制 / 删除）：引用发送时固化内容快照（`quote_id` + `quote_content`，校验被引用消息须属当前会话，原消息删除后引用仍可显示）、相对时间展示（刚刚 / N 分钟前 / N 小时前 / 昨天 / 前天 / 其他具体时间）；单侧删除——`sender_deleted` / `receiver_deleted` 双标记列，删除消息或删除会话仅本人视角不可见（会话汇总、未读统计同步过滤），对方不受影响 |
| 国际化 | 前端中英双语（`zh-CN` / `en-US`）：认证、系统管理、监控、文件、仪表盘、个人中心、AI 助手全量文案走 vue-i18n 语言包（`apps/web-ele/src/locales/langs/`），头部一键切换、刷新持久；侧边栏菜单标题（数据库存 i18n key）随语言同步切换；后端错误消息同步双语——前端每请求携带 `Accept-Language`，后端 BizException/校验注解/幂等限流/401 均按请求语言返回（`messages.properties` 中文兜底 + `messages_en_US.properties`，约 120 条消息 key） |
| API 文档 | springdoc 自动生成 OpenAPI 3 + Swagger UI（`/api/swagger-ui/index.html`，dev 开启 / prod 关闭） |
| AI 助手 | 真 Agent（plan-and-execute）：**21 个内置工具注解自动注册**（Service 方法标 `@AiAgentTool` 即成为 AI 工具，零 switch 适配），工具按登录用户权限码**动态可见可调**；AI 自主多步规划——多写操作任务先产出执行计划（目标+步骤+理由），前端计划卡确认后 SSE 步骤流顺序执行（等待/执行中/成功/失败实时反馈）；高危操作（删除用户/角色/部门、重置密码、强退、菜单授权）计划卡单独标红 + 执行前**二次确认弹框**，服务端 Redis 暂存 10 分钟；任一步失败即终止、每步执行前再校验权限；单写操作仍走轻量确认卡 |
| 模型配置 | AI 底层模型可插拔（系统管理 → 模型配置）：自定义添加任意 OpenAI 兼容协议模型（DeepSeek / Qwen / Kimi / GLM / Ollama / one-api 等），可配接口地址、API Key（明文入库、接口回显脱敏、编辑留空不修改）、模型名、温度 / 最大 token / 超时；一键连通测试（非流式 ping 返回耗时与回显）；全局唯一激活——AI 助手对话与摘要的底层模型即时切换，无需重启；未配置激活模型时回退 yml `DEEPSEEK_API_KEY` 兜底 |
| 一键部署 | Docker Compose 编排（PG + Redis + 后端 + 前端 nginx 同源反代）：`docker compose up -d` 起全套演示环境，docker profile 自动建库、安全开关默认关闭 |
| CI | GitHub Actions 双流水线：后端 `mvn test`（幂等切面/IP 工具等单测）+ 前端 `pnpm build`，push/PR 自动执行 |

## 快速开始

### 方式一：Docker 一键部署（推荐演示）

```bash
docker compose up -d
```

访问 http://localhost:5888 ，账号见下表。说明：

- 编排四服务：PostgreSQL + Redis + 后端（Spring Boot，docker profile）+ 前端（nginx 托管产物并 `/api` 同源反代），首次构建需拉取基础镜像并全量构建，耗时较长
- 与本地开发完全隔离：前端 **5888**、后端直连 **18080**（调试用），PG/Redis 不映射宿主端口；数据存 named volume（`vben-deploy-*`），不影响开发容器 vben5 / vben-redis，`docker compose down -v` 可彻底清空
- 首次启动自动建表 + 种子数据；验证码回显、SQL 日志、API 文档均已关闭（与生产语义一致）
- AI 助手：优先登录后在「系统管理 → 模型配置」添加并激活模型（OpenAI 兼容协议）；也可宿主机 `setx DEEPSEEK_API_KEY "<你的Key>"` 后**重开终端**再执行 compose 命令作为兜底，两者均未配置时仅 AI 功能提示未配置
- 镜像构建走国内镜像源前缀 `docker.1ms.run`，海外环境可自行去掉；基础镜像拉取或 pnpm 安装受限时参照文件内注释换源

### 方式二：本地开发

#### 环境要求

- JDK 21、Maven 3.9+（全局安装即可）
- Node.js 22.18+ / 24、pnpm 10+（以 `front/package.json` 的 engines 为准）
- Docker（运行 PostgreSQL 与 Redis）

#### 1. 启动数据库与 Redis

```bash
docker run -d --name vben5 -p 5444:5432 -e POSTGRESQL_PASSWORD=123456 -e POSTGRESQL_DATABASE=vben5 bitnami/postgresql:18
docker run -d --name vben-redis -p 6379:6379 -v vben-redis-data:/data redis:7-alpine --appendonly yes   # 国内拉不动可换镜像源，如 docker.1ms.run/library/redis:8.8.0
```

> 表结构与种子数据由后端启动时自动执行（`schema-postgres.sql` + `data.sql`，全部幂等可重复执行）。
> Redis 承载验证码/登录锁定/限流/token 版本号/在线会话，后端启动时连不上会直接失败。

#### 2. 启动后端（端口 8080）

```bash
mvn spring-boot:run -f service/pom.xml
```

#### 3. 启动前端（端口 5777）

```bash
cd front
pnpm install
cd apps/web-ele
pnpm dev
```

访问 http://localhost:5777 ，默认账号：

| 账号 | 密码 | 说明 |
|---|---|---|
| vben | 123456 | 超级管理员（全部权限） |
| stockAdmin | 123456 | 数据权限演示（仅库存部门数据） |

> AI 助手需要在「系统管理 → 模型配置」添加并激活模型；也可设置环境变量 `DEEPSEEK_API_KEY` 兜底（见注意事项）。

## AI 智能助手

登录后点击右下角悬浮按钮打开。能力与设计：

- **21 个工具、注解零适配注册**：业务 Service 方法标 `@AiAgentTool`（名称/标题/类型/权限码/是否高危）即自动成为 AI 工具，启动时反射扫描注册、JSON Schema 由方法签名生成——新增工具不再需要改任何注册代码。覆盖：用户/角色/部门/菜单/字典/文件/在线用户/登录日志/操作日志/仪表盘查询，用户/角色/部门/公告创建，用户/角色/部门删除，密码重置，在线强退，角色菜单授权
- **工具按权限动态下发**：每次对话按当前用户权限码过滤工具清单，无权限的工具模型根本"看不见"；执行接口独立二次校验（前端按钮不是安全边界）
- **plan-and-execute 真 Agent**：多写操作任务 AI 先产出执行计划（目标 + 步骤 + 理由），前端计划卡展示全貌，确认后 SSE 步骤流顺序执行并实时反馈每步状态；任一步失败立即终止；每步执行前再次校验权限
- **高危操作双保险**：删除用户/角色/部门、重置密码、强退、菜单授权标记 `danger=true`——计划卡单独标红，执行到该步时服务端暂停（Redis 暂存 10 分钟），用户二次确认后才继续
- **两档轻量路径**：单写操作仍走确认卡片；纯查询自动执行并把真实数据回喂模型
- **上下文记忆**：最近 50 条消息按完整轮次截断（不会切断工具调用配对），更早轮次由大模型滚动摘要为"此前对话摘要"注入；会话按用户隔离持久化在浏览器本地，刷新不丢
- **数据安全**：查询结果字段裁剪（不含密码等敏感字段）；写操作复用既有业务层校验与自动填充

## 目录结构

```
├── front/                    # 前端（Vben Admin 5.7 monorepo）
│   └── apps/web-ele/         # 主应用：Vue3 + Element Plus（AI 助手在 src/components/ai-assistant/）
├── service/                  # 后端（Spring Boot 3.5）
│   └── src/main/java/com/vben/service/
│       ├── bootstrap/        # 启动与数据初始化（种子数据）
│       ├── common/           # 统一响应/异常/操作日志切面/storage 存储抽象（local 实现）
│       ├── config/           # Web/MyBatis-Plus 配置
│       ├── security/         # JWT 过滤器、权限拦截器、登录用户上下文
│       └── module/           # 业务模块：auth / user / system(角色部门) / menu / monitor / ai
│   └── src/main/resources/db/  # schema-postgres.sql / data.sql（幂等）
└── openspec/                 # 规范驱动开发：变更提案与归档
```

## 注意事项

### 安全红线（部署/公开前必做）

1. **更换 JWT 密钥**：`application.yml` 中 `vben.jwt.*-token-secret` 是占位值，生产必须替换为至少 32 字节随机串，且 access/refresh 使用不同密钥
2. **大模型 Key 安全**：优先在「模型配置」页添加模型（Key 明文入库、接口回显脱敏，生产库请做好访问控制）；yml 兜底 Key 只走环境变量（`${DEEPSEEK_API_KEY:}`，无默认值），本地启动需 `setx DEEPSEEK_API_KEY "<你的Key>"` 后重开终端；**历史提交中出现过旧 Key，公开仓库前必须到 DeepSeek 控制台作废**
3. **修改默认账号密码**：vben / stockAdmin 等种子账号仅用于演示
4. 数据库密码、Cookie 安全策略（`same-site`/`secure`）按部署形态调整：本地 http 用 `Lax + false`，https 跨域部署用 `None + true`
5. 生产配置切 `application-prod.yml`（MySQL），关闭 MyBatis-Plus SQL 控制台打印；prod 已自动关闭 API 文档端点

### 开发须知（踩坑经验）

- **先起 Docker 再起后端**：PostgreSQL 跑在容器 `vben5`（端口 5444）、Redis 跑在容器 `vben-redis`（端口 6379），任一容器未启动后端都会直接失败
- **Redis 不要随意 FLUSHALL**：token 版本号与在线会话存于 Redis，清空会让已"踢下线/改密"用户的旧 token 重新变为有效（回到自然过期语义）
- **前端端口固定用 5777**：后端 CORS 白名单只放行 `localhost:5777` / `5666`（`application.yml > vben.cors`），换端口会被拦截
- **不要用数据库触发器填充时间字段**：Spring SQL 初始化器按分号切 SQL 会截断 PostgreSQL `$$...$$` 函数体，createTime/updateTime 由 `MetaObjectHandler` 在应用层自动填充
- **AI 会话窗口必须按轮截断**：禁止按消息条数硬切（会把 `tool_calls` 与 `tool` 结果拆散导致上游 400），窗口逻辑见 `front/apps/web-ele/src/components/ai-assistant/context-window.ts`
- **前端按钮权限**：新增按钮时必须配套使用 `v-access:code` 指令 + 菜单管理里登记权限码
- **多根节点组件**：在 `<Transition>`/`<KeepAlive>` 内使用的组件必须有单一根元素，否则动画与属性继承失效
- **文件上传/下载**：下载接口 `GET /file/{id}/content` 需登录（未加入 JwtAuthFilter 白名单）；扩展名白名单不校验文件魔数，生产部署请在 Nginx 层禁止上传目录执行脚本，且大文件场景建议改为对象存储签名直链；本地存储目录默认 `./files`（已加入 .gitignore）
- **WebSocket 单机边界**：站内通知与 IM 聊天的推送注册表是单机内存（`NoticeWebSocketHandler` / `ImWebSocketHandler`），多实例部署需升级为 Redis pub/sub 广播；nginx 反代 `/api/` 已带 WS 升级头

### 生产部署 Checklist

- [ ] 更换 JWT 密钥、数据库密码、所有默认账号密码
- [ ] 在「模型配置」页添加并激活模型，或注入 `DEEPSEEK_API_KEY` 环境变量兜底（yml 无默认值）；作废历史提交中暴露过的旧 Key
- [ ] 确认 `vben.captcha.echo-enabled` 为 false（prod 默认关闭，勿在 prod 开启验证码回显）
- [ ] 数据库以 PostgreSQL 为准（`schema-postgres.sql`）；`application-prod.yml` 中的 MySQL 配置仅为预留，未经验证，生产部署请改写为 PostgreSQL 或先完成验证
- [ ] 演示/交付环境可直接用根目录 `docker compose up -d`（docker profile：PostgreSQL 自动建库 + 安全开关关闭）；生产公网部署请另行评估并更换全部演示默认值
- [ ] 前端 `pnpm build:ele` 产物走 nginx，`/api` 反代到 8080
- [ ] Cookie `same-site=None + secure=true`
- [ ] 关闭 SQL 日志与 debug 级别日志

## 相关文档

- [变更归档](openspec/changes/archive/) —— 每个功能的 proposal / design / spec / tasks 全过程记录
