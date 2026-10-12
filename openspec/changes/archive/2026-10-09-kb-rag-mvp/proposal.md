## Why

AI 助手目前只能回答「系统里有什么」类问题（查库工具），无法回答「项目文档里怎么写」类问题——系统的业务说明、操作手册、制度规范都散落在文档里，模型完全看不到。为 AI 助手补上 RAG（检索增强生成）能力：管理员上传 md/txt 文档，系统切块向量化入库，对话时自动检索相关片段作为上下文，回答可溯源到具体文档。这也是项目从「工具调用 Agent」走向「知识增强 Agent」的第一步。

## What Changes

- 新增 Embedding 模型配置：与既有 `sys_llm_config` 同模式的姊妹配置（OpenAI 兼容 `/v1/embeddings` 协议，全局唯一激活），支持连通性测试与 Key 脱敏。LLM 用于对话，Embedding 用于向量化，二者解耦（DeepSeek 无 embedding API，需可独立选型如 bge-m3 / text-embedding-3-small）
- 新增知识库模块（MVP）：文档上传（仅 .md/.txt，≤10MB）→ 智能切块（md 按标题层级、txt 按长度窗口）→ 异步批量向量化 → pgvector 存储；文档列表支持状态跟踪（处理中/完成/失败）与删除（级联清理 chunk）
- 新增向量检索：基于 pgvector 余弦距离（`<=>`）Top-K 检索，MVP 规模（数千 chunk）暴力扫描即可，不建向量索引
- AI 助手新增 `search_knowledge` 查询工具：模型遇知识类问题时自动检索，回答需注明出处（文档名 + 段落）
- 新增两个管理页面：系统管理下「Embedding 配置」（order 9）与「知识库」（order 10），含检索测试入口
- 存量库升级 SQL + 种子数据同步（DatabaseSeeder 与手动 SQL 两处一致）

**Non-Goals（本期不做，留二期）**：pdf/docx 等格式解析、多知识库分区、rerank 精排、HNSW 向量索引、独立问答页、文档手动编辑。

## Capabilities

### New Capabilities

- `system/embedding-config`: Embedding 模型配置的增删改查、全局唯一激活、连通测试、Key 脱敏（对齐 `system/llm-config` 的全部安全约定）
- `ai/knowledge-base`: 知识库文档管理（上传、切块、向量化状态机、删除）与向量检索接口（Top-K 相似片段查询）

### Modified Capabilities

- `ai/assistant`: 新增「知识检索工具」Requirement（ADDED）——`search_knowledge` 查询类工具语义、无权限码（登录即可）、回答溯源要求

## Impact

- **后端**：`module/ai/llm/` 新增 embedding 端点与配置实体；新建 `module/ai/kb/`（controller/service/entity/mapper/async pipeline）；`LlmClient` 旁新增 EmbeddingClient（JDK HttpClient，零新增依赖）；`schema-postgres.sql` 增加 `CREATE EXTENSION IF NOT EXISTS vector` 与 `kb_doc`/`kb_chunk`/`sys_embedding_config` 三表；`DatabaseSeeder` 增菜单与权限码；`AiSystemTools.MENU_TITLE_ZH` 同步；`messages*.properties` 双语错误文案
- **前端**：`views/system/embedding/`、`views/system/kb/` 两个新页面；locales 中英文件同构（`page.system.embedding`/`page.system.kb`）；`api/` 下新增两个模块
- **数据库**：依赖 pgvector 扩展（dev 容器 bitnami/postgresql:18 实测含 0.8.3；docker-compose 部署栈同镜像系，需实施时验证）；新表通过 schema 初始化器 + 存量库 upgrade SQL 双通道落地
- **权限码**：`System:Emb:List/Add/Edit/Delete/Activate`、`Kb:Doc:List/Upload/Delete/Test`（F 型挂对应菜单）
