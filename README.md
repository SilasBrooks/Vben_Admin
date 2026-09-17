# Vben Admin 管理系统

> 企业级中后台管理系统：**Spring Boot 3 (JDK 21) + MyBatis-Plus + PostgreSQL** 后端，**Vue 3 + Vite + Element Plus（Vben Admin 5.7）** 前端，内置**按钮级 RBAC 权限体系**与 **AI 智能助手（DeepSeek Agent 编排）**。
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
| AI | DeepSeek Chat（OpenAI 兼容协议，SSE 流式 + Function Call） |

## 功能清单

| 模块 | 内容 |
|---|---|
| 认证 | 登录 / 双 Token 刷新 / 登出 / BCrypt 密码加密 / 凭证变更与强退后旧 token 即时失效（版本号机制） |
| 登录安全 | 服务端图形验证码（一次性 + 2 分钟有效期）、失败锁定（用户名/IP 双维度 15 分钟 5 次）、声明式接口限流 `@RateLimit`——状态存 Redis，重启不丢、多实例共享 |
| 系统管理 | 用户、角色、菜单、部门管理（CRUD + 树形结构 + 分页） |
| 权限 | 按钮级 RBAC 权限码、菜单驱动动态路由、角色授权 |
| 数据权限 | 部门粒度行级数据隔离（stockAdmin 角色为演示账号） |
| 监控 | 操作日志、登录日志（声明式采集）、在线用户（实时会话列表 + 强制下线） |
| 数据字典 | 可维护字典 + 前端 `useDict` hook（自动缓存共享） |
| 文件存储 | 可插拔存储抽象（`StorageService`，本地实现起步，预留 OSS/MinIO）、扩展名白名单 + 10MB 上限 + UUID 随机存储名、文件管理页（列表/上传/预览/删除）、头像上传接入 |
| 仪表盘 | 工作台/分析页真实数据版（`GET /dashboard/summary` 登录即可全员同版）：统计卡 + 今日概况 + 近 14 天登录趋势 + 部门/模块分布 + 最近登录/操作，替代模板演示数据 |
| API 文档 | springdoc 自动生成 OpenAPI 3 + Swagger UI（`/api/swagger-ui/index.html`，dev 开启 / prod 关闭） |
| AI 助手 | 自然语言查询/新增用户、角色、部门，角色菜单授权；详见下文 |

## 快速开始

### 环境要求

- JDK 21、Maven 3.9+（全局安装即可）
- Node.js 20+、pnpm 9+
- Docker（运行 PostgreSQL 与 Redis）

### 1. 启动数据库与 Redis

```bash
docker run -d --name vben5 -p 5444:5432 -e POSTGRESQL_PASSWORD=123456 -e POSTGRESQL_DATABASE=vben5 bitnami/postgresql:18
docker run -d --name vben-redis -p 6379:6379 -v vben-redis-data:/data redis:7-alpine --appendonly yes   # 国内拉不动可换镜像源，如 docker.1ms.run/library/redis:8.8.0
```

> 表结构与种子数据由后端启动时自动执行（`schema-postgres.sql` + `data.sql`，全部幂等可重复执行）。
> Redis 承载验证码/登录锁定/限流/token 版本号/在线会话，后端启动时连不上会直接失败。

### 2. 启动后端（端口 8080）

```bash
mvn spring-boot:run -f service/pom.xml
```

### 3. 启动前端（端口 5777）

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

> AI 助手需要配置大模型 Key：设置环境变量 `DEEPSEEK_API_KEY`（见注意事项）。

## AI 智能助手

登录后点击右下角悬浮按钮打开。能力与设计：

- **8 个工具、两档执行语义**：查询类（用户/角色/部门/菜单）自动执行并把真实数据回喂模型；新增类（用户/角色/部门、角色菜单授权）生成**确认卡片**，用户确认后才调用既有 Service 落库
- **安全边界在服务端**：写操作下发确认卡片前先校验当前用户权限码，无权限直接回复"你的权限不足"；确认执行接口独立二次校验（前端按钮不是安全边界）
- **上下文记忆**：最近 50 条消息按完整轮次截断（不会切断工具调用配对），更早轮次由 DeepSeek 滚动摘要为"此前对话摘要"注入；会话按用户隔离持久化在浏览器本地，刷新不丢
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
├── docs/
│   └── tech-overview.md      # 技术架构全景说明（模块级细节）
└── openspec/                 # 规范驱动开发：变更提案与归档
```

## 注意事项

### 安全红线（部署/公开前必做）

1. **更换 JWT 密钥**：`application.yml` 中 `vben.jwt.*-token-secret` 是占位值，生产必须替换为至少 32 字节随机串，且 access/refresh 使用不同密钥
2. **大模型 Key 只走环境变量**：配置已无默认 Key（`${DEEPSEEK_API_KEY:}`），本地启动前需 `setx DEEPSEEK_API_KEY "<你的Key>"` 后重开终端；**历史提交中出现过旧 Key，公开仓库前必须到 DeepSeek 控制台作废**
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

### 生产部署 Checklist

- [ ] 更换 JWT 密钥、数据库密码、所有默认账号密码
- [ ] `DEEPSEEK_API_KEY` 环境变量注入（配置无默认值）；作废历史提交中暴露过的旧 Key
- [ ] 确认 `vben.captcha.echo-enabled` 为 false（prod 默认关闭，勿在 prod 开启验证码回显）
- [ ] `application-prod.yml`（MySQL）并执行对应 schema
- [ ] 前端 `pnpm build:ele` 产物走 nginx，`/api` 反代到 8080
- [ ] Cookie `same-site=None + secure=true`
- [ ] 关闭 SQL 日志与 debug 级别日志

## 相关文档

- [技术架构全景说明](docs/tech-overview.md) —— 后端每个模块的职责、实现位置与设计细节
- [变更归档](openspec/changes/archive/) —— 每个功能的 proposal / design / spec / tasks 全过程记录
