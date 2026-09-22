# AGENTS.md

本文件供 AI 编码助手（Codex / Claude / Trae 等）读取，是本仓库的强制协作规则。换电脑、换工具时以本文件为准，勿依赖任何会话记忆。

## 1. Git 分支策略（必须遵守）

- 工作分支是 `gy`：所有提交、推送只针对 `origin/gy`
- **禁止同时推送 `master`**（包括 `gy:master` fast-forward）；是否把 `gy` 合入 `master` 由用户自行决定
- 提交信息用单行中文，概括「为什么」而非罗列改动清单
- 仅在用户明确要求提交时才 commit / push，不主动提交

## 2. 项目结构与技术栈

| 目录 | 内容 |
|------|------|
| `service/` | Spring Boot 3 + Spring Security 6 + MyBatis-Plus 3.5 + PostgreSQL + Redis（Maven） |
| `front/` | Vben Admin pnpm monorepo（Vue3 `<script setup>` + TS + Element Plus + vxe-table） |
| `front/apps/web-ele/` | 唯一业务应用；`front/packages/`（@vben/\* 框架包）一般不改 |
| `.github/workflows/ci.yml` | CI：backend `mvn test` + frontend `pnpm build web-ele` |

- 后端 dev 端口 8080，API 前缀 `/api`；前端 dev 端口 5777（`pnpm dev:ele`）
- PG 容器 `vben5`（127.0.0.1:5444，库 `vben5`，postgres/123456）；Redis 容器 `vben-redis`（6379，key 统一 `vben:` 前缀）
- 种子数据 `DatabaseSeeder` 仅空库执行（`sys_user` 非空即 return）：内置账号 vben / admin / jack / stockAdmin（密码均 123456）；**存量库加菜单/字段需手动 SQL 同步**
- AI 底层模型走「系统管理→模型配置」（`sys_llm_config`，OpenAI 兼容协议，全局唯一激活即时切换）：Key 明文入库、接口回显脱敏、编辑留空不修改；yml `DEEPSEEK_API_KEY` 环境变量仅为无激活配置时的兜底；Key 一律不进 git

## 3. 环境启动与排查

- 后端启动依赖 Docker Desktop + 容器 `vben5`、`vben-redis`：报 Connection refused 先查 Docker Desktop，再 `docker start vben5 vben-redis`
- 后端：`service/` 下 `mvn spring-boot:run`；前端：`front/` 下 `pnpm install` 后 `pnpm dev:ele`
- 后端莫名全 500 且日志报 `Unresolved compilation problems`：`mvn -q clean compile` 后重启
- Windows PowerShell 5.1 坑：不支持 `&&`（用 `;`）和 heredoc；curl 传 JSON 先写临时文件再 `-d "@file"`，multipart 用 `curl.exe -F`
- Docker Hub 拉不到镜像按顺序换源：`docker.1ms.run` → `daocloud` → `1panel.live`

## 4. 编码约定

### 前端
- **组件使用优先级**：优先使用 `front/packages/` 下已封装的组件库（`@vben/common-ui` 的 Page / VbenButton / useVbenModal、`#/adapter` 适配的 vxe-table / 表单组件等），**禁止直接使用 Element Plus 原始组件**；仅当特殊定制开发且现有封装组件无法满足需求时才允许使用原始组件。使用封装组件时必须遵循其使用规范与最佳实践（参照 `views/system/user` 等既有页面写法），保证项目 UI 一致性与代码可维护性
- 按钮权限用 `v-access:code` 指令 + 权限码（如 `System:User:Add`）
- `<Transition>` / `<KeepAlive>` 内组件必须单根节点；多根组件设 `inheritAttrs: false` 并在目标根元素 `v-bind="$attrs"`
- vxe-table：搜索表单默认 `showCollapseButton: false`；树表用 treeConfig + childrenField（`transform` 模式不支持嵌套 children）；调 grid API（如 query()）前包 `nextTick()`
- 菜单标题（`sys_menu.title`，M/C 型）存 vue-i18n key（`page.*` 命名空间）；语言包在 `front/apps/web-ele/src/locales/langs/{zh-CN,en-US}/`，**文件名即顶级命名空间**，中英文件必须同构；F 型权限码 title 保持明文；新增带 key 的菜单需同步后端 `AiSystemTools.MENU_TITLE_ZH`（AI 对话的中文还原与菜单名匹配）
- 主题/语言等用户偏好刷新后必须持久化；偏好合并顺序 overrides > cachedPreferences > defaultPreferences
- 头像/文件上传用 ElUpload 托管（`http-request` 自定义上传），不要手写 hidden input
- 表单 schema 里没有 `Textarea` 组件：用 `'Input'` + `type: 'textarea'`
- 时间展示去掉 LocalDateTime 微秒尾巴：`value.split('.')[0]`
- 全局 404 回退路由必须包 BasicLayout（保持侧边栏/头部）

### 后端
- **Mapper 必须放模块的 `mapper` 子包**（`@MapperScan("com.vben.service.module.**.mapper")` 只扫该模式）：放模块根包等他处启动即报 `No qualifying bean of type` 失败
- 业务错误文案走 `BizException.badRequest("error.<模块>.<语义>")` + `messages.properties` / `messages_en_US.properties` 中英双语同步（支持 `{0}` 占位参数），禁止在 Java 里硬编码中文文案
- 新增 AI 工具用 `@AiAgentTool` 注解声明（`module/ai/tool/` 下 `@Component` 类即自动注册，含权限码与参数说明），不要往旧硬编码执行器 `AiToolExecutor` 加 case
- 登录安全、在线会话、幂等等分布式状态必须走 Redis（fail-fast，不降级）；key 集中在 RedisKeys 管理
- 写操作接口加 `@Idempotent` 防重复提交（409）；限流用 `@RateLimit`
- 文件上传：扩展名白名单（GENERAL/IMAGE）+ 10MB 上限 + UUID 随机名 + 限流；存储可插拔（`vben.file.storage=local|minio`）；本地目录 `files/` 已 gitignore
- Token 版本号存 Redis：改密/重置密码/禁用账号/强退必须 bump，旧 token 即时 401
- JWT refresh cookie 回退认证仅允许 `GET /file/*/content`（头像直链），其余无 Bearer 一律 401
- GlobalExceptionHandler：`NoResourceFoundException`→404、`HttpMessageNotReadableException`→400，勿落入兜底 500
- 新增菜单/权限码需同步 `DatabaseSeeder` 与存量库 SQL（两处一致）
- SQL 初始化器会截断 `$$...$$` dollar-quoted 函数：updateTime 用 MetaObjectHandler，不用数据库触发器

## 5. openspec 工作流（功能变更必须走）

- 新增/修改功能（新模块、AI 工具、跨前后端改造、协议/表结构变更）必须使用 openspec 工作流规划，**禁止用 `.trae/documents` 等临时计划文件替代**
- 变更目录 `openspec/changes/<slug>/`：`proposal.md`（Why / What Changes / Capabilities / Impact）、`design.md`（Context / Goals-NonGoals / Decisions / Risks）、`tasks.md`（可勾选任务清单）、`specs/<capability>/spec.md`（delta：ADDED / MODIFIED Requirements）；`.openspec.yaml` 写 `schema: spec-driven` + `created: 日期`
- delta 文案用中文，SHALL / MUST 等结构化关键词保持英文；MODIFIED 需求必须给出修改后的完整需求文本（含全部 Scenario），capability 归属参照 `openspec/specs/` 既有目录（如 `system/llm-config`、`ai/assistant`），新能力建新目录
- 归属判定：系统能力（菜单/配置/字典类）放 `system/*`，AI 助手行为放 `ai/assistant` 的 MODIFIED；一次变更可同时新建能力 + 修改既有能力
- 实现完成并验证后：`openspec validate <slug> --strict` 通过 → `openspec archive <slug> --yes`（自动 sync delta 到 `openspec/specs/` 并移入 `changes/archive/<日期>-<slug>/`）；tasks.md 末项写「归档 openspec 变更（sync delta → archive）」
- 归档前不 commit 代码（与第 6 节提交纪律一致）：openspec 归档产物与实现代码属同一变更，随代码一起提交

## 6. 验证与提交

- 提交前自检：前端 `front/` 下 `pnpm check:type`；后端 `service/` 下 `mvn -q compile`，改逻辑需 `mvn test`
- CI push 自动触发；pnpm/action-setup@v4 必须传 `package_json_file: front/package.json`（否则装 pnpm 9 与 engines 冲突秒败）、Node ≥ 22.18
- 新增功能同步根 README.md 的「项目亮点 / 功能清单 / 注意事项」

## 7. E2E / 浏览器测试要点

- dev 验证码接口字段是 `captchaId`（非 uuid），dev 响应带 `devCode` 明文回显；docker profile 无 devCode，改用 `docker exec vben-deploy-redis redis-cli GET vben:captcha:{captchaId}` 直读
- 浏览器注入登录前必须清 localStorage + cookie（残留他账号数据会 redirect 循环）；语言切换必须走真实 UI 入口，勿手动改 localStorage locale（合法值仅 zh-CN / en-US）
- vxe-table `fixed: 'right'` 操作列在 DOM 有空复制层：判断按钮存在用 `document.body.innerText` 全量取证，别只查单个 cell
- 限流/锁定 E2E 用 `X-Forwarded-For` 虚拟 IP 隔离状态，避免锁死 127.0.0.1 与真实账号
- 浏览器 console_messages 跨导航聚合缓冲：旧警告会混进新页面读取，干净判定要新开标签页
