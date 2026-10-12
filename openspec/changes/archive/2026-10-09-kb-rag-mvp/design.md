## Context

AI 助手已具备「模型配置 + 工具循环」骨架：`sys_llm_config` 全局唯一激活（`SysLlmConfigService`/`ActiveLlmResolver`/`LlmClient`），工具经 `@AiAgentTool` 注解自动注册，查询工具同请求内自动执行回喂。知识库只需在这个骨架上挂两个新支点：一个独立的 Embedding 配置（姊妹于 llm-config），一个向量检索数据通道（pgvector）+ 一个查询工具。

约束：
- dev 库容器 `vben5`（bitnami/postgresql:18）实测含 pgvector 0.8.3，`CREATE EXTENSION vector` 即可用
- SQL 初始化器会截断 `$$...$$` dollar-quoted 函数——本变更不使用任何数据库触发器/函数
- DeepSeek（默认对话模型）无 embedding API，Embedding 必须可独立选型
- JDK HttpClient + 零新增后端依赖是既定惯例（LlmClient 先例）

## Goals / Non-Goals

**Goals:**
- Embedding 配置与 LLM 配置同安全等级：脱敏回显、激活互斥、留空不覆盖 Key
- 上传 → 切块 → 向量化全链路异步化，状态机可观测（处理中/完成/失败 + 失败原因）
- 检索延迟 MVP 规模下可接受（数千 chunk 暴力扫描 + 上游一次 embedding 调用）
- AI 对话零改动融入：search_knowledge 作为普通查询工具自动执行

**Non-Goals:**
- 不做多知识库/目录树分区、rerank 精排、HNSW 索引、混合检索（BM25+向量）
- 不解析 pdf/docx（字符流直读 md/txt 即可）
- 不做检索结果缓存、不做文档手动编辑

## Decisions

### D1：pgvector + 无固定维度向量列
`kb_chunk.embedding` 用 `vector` 类型但**不定维度**（pgvector 允许无 typmod 定义）。原因：维度由用户所选 embedding 模型决定（bge-m3=1024、text-embedding-3-small=1536），定死维度会锁死选型。检索时以参数 cast（`?::vector`）比较，查询前先校验查询向量维度与该文档库已入库维度一致（以 chunk 表中现存维度为准），不一致报可读错误。
- 备选「固定 vector(1536) 列」：拒绝，锁死模型选型。
- 备选「JSONB 存向量 + Java 内存余弦」：拒绝，放弃 pgvector 检索语义（`<=>` 距离排序在 SQL 内完成），且与「RAG 向量检索」目标不符。
- MVP 不建 HNSW/IVFFlat 索引：无 typmod 列不能建向量索引，MVP 规模暴力扫描足够；二期固定维度后可加列+索引迁移。

### D2：Embedding 配置独立成表 `sys_embedding_config`，不扩展 sys_llm_config
复制 llm-config 的全套模式（脱敏 `maskKey`、激活互斥事务、ping→embedding 连通测试、留空不覆盖 Key），但独立表 + 独立激活位。原因：对话与向量化选型天然解耦（DeepSeek 对话 + 硅基流动/OpenAI 向量化是常态），塞进一张表要用 kind 字段区分两组激活，激活互斥语义复杂化，还侵入既有 `SysLlmConfigService` 全部查询。独立表零回归风险，代码上以 llm-config 为模板复制精简（不做 temperature/maxTokens 等对话特有字段，新增 dimension）。

### D3：EmbeddingClient 独立类，LlmClient 不动
`LlmClient` 职责是 SSE 流式对话，请求/响应形态与 embeddings 完全不同（非流式、返回向量数组）。新建 `EmbeddingClient`（同用 JDK HttpClient，POST `/embeddings`，URL 拼接规则复用 LlmClient 的 baseUrl 归一化思路），输入文本数组 → 输出向量列表。批量每批 ≤16 条（OpenAI/硅基流动常见上限），失败映射为可读中文错误（对齐 mapHttpError 风格）。

### D4：切块策略——标题优先，长度兜底，少量重叠
- Markdown：按 `^#{1,3} ` 标题行切主块（保留标题行在块头），单块 >800 字符再按空行段落二次切，仍超长按 800 字符硬切
- 纯文本：按空行段落聚合到 ≤800 字符，段落超长硬切
- 相邻块重叠 ~80 字符（硬切时才产生），保跨块上下文
- 切块参数本期常量内聚在切块器内，不做成配置（MVP 保持简单）

### D5：异步管线用 Spring @Async + 单线程池
新建 `KbIngestExecutor`（线程池 1 worker，队列有界如 50），文档上传受理后投递异步任务：读正文 → 切块 → 批量 embedding → 逐批落库 → 状态完成；任何异常 → 状态失败 + 原因。单 worker 串行天然避免并发写同一文档与上游限流叠加。
- 备选「同步处理」：拒绝，大文档几十秒必然拖垮 HTTP 请求超时。
- 备选「Redis 队列」：拒绝，MVP 过度设计；应用重启丢队列属可接受（状态留「处理中」，用户删除重传即可，列表页对长期「处理中」文档可见可判断）。
- 删除拦截：处理中拒删（与 spec 一致），避免 worker 与删除并发竞态。

### D6：检索 SQL 与 AI 工具
- 检索：`SELECT ... ORDER BY embedding <=> CAST(#{queryVector} AS vector) LIMIT #{k}`（MyBatis 注解 SQL，参数为字符串化的向量字面量），相似度 = `1 - distance`
- AI 工具 `search_knowledge` 放 `module/ai/tool/AiKnowledgeTools.java`（新 @Component），QUERY kind、无权限码；回喂内容含文档名/块序号/正文，Top-4、单块正文截断 500 字符，总回喂上限 3000 字符
- 检索测试 REST 复用同一 service 方法（`/ai/kb/search` 或挂知识库 controller，实施时按模块归属定）

### D7：模块归属与页面
- 后端新包 `module/ai/kb/`（controller/service/entity/mapper + ingest 子包）；embedding 配置放 `module/ai/llm/` 旁新建 `module/system/embedding/`？——不，embedding 配置服务对象是 AI 管线，归 `module/ai/` 域（`module/ai/emb/`），菜单挂系统管理下与 llm 对称（`/system/embedding`）
- 菜单：系统管理 leaf order 9「Embedding 配置」（`SystemEmb`，`/system/embedding`）、order 10「知识库」（`SystemKb`，`/system/kb`），均 super,admin；权限码 `System:Emb:*`、`Kb:Doc:*`
- `AiSystemTools.MENU_TITLE_ZH` 同步两条菜单中文名

### D8：存量库与初始化双通道
- `schema-postgres.sql`：顶部 `CREATE EXTENSION IF NOT EXISTS vector;`（幂等，普通语句无截断风险）+ 三张新表
- 新增 `db/upgrade-20261009-kb-rag-postgres.sql`：同样内容（扩展 + 三表 + 菜单/权限码 INSERT），供存量库手动执行；`schema-mysql.sql` 不动（MySQL 无 pgvector，本能力明确仅支持 PostgreSQL，README 注意事项写明）
- 注意：MySQL schema 若与 PG 初始化器共用启动逻辑，需确认初始化器对 MySQL 跳过 pgvector 语句（实施时核实启动路径，H2/MySQL 环境知识库功能降级不可用属预期）

## Risks / Trade-offs

- [部署栈 pgvector 不可用] docker-compose 用 bitnami/postgresql:latest，与 dev 同系大概率含 vector；实施时先 `pg_available_extensions` 验证，不含则换 pgvector 镜像或在 README 标注 → 实施第一步验证
- [无维度列不能建向量索引] 数据量大后检索变慢 → MVP 规模（数千块）暴力扫描 <50ms 可接受；二期固定维度加 HNSW
- [文档切换 embedding 模型后新旧向量维度混存] 检索时维度不一致 → 查询前校验维度，混存期间提示重新处理旧文档（MVP 不做自动重建，文档可删除重传）
- [应用重启丢异步任务] 处理中文档停留「处理中」 → 列表可见可判断，删除重传；MVP 接受
- [上游 embedding 批量限制差异] 各家批上限不同 → 批大小常量 16 偏保守，超限报错可读
- [schema-mysql 共用初始化器] pgvector 语句在 MySQL 侧报错 → 实施时核实初始化路径，必要时按数据源类型条件执行

## Migration Plan

1. 实施首步验证部署镜像 pgvector 可用性（决定是否调整部署镜像）
2. schema 初始化器 + upgrade SQL 双通道加扩展与三表（存量库手动执行 upgrade SQL）
3. 上线后先配置并激活 Embedding 模型，再上传文档
4. 回滚：新表与扩展独立，不碰既有表；回滚仅需停用功能（不激活即可），无破坏性迁移
