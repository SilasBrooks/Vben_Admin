## 1. 数据库与前置验证

- [x] 1.1 验证部署栈镜像 pgvector 可用性：bitnami/postgresql:18 自带 vector 0.8.3（`CREATE EXTENSION vector` 在 vben5 与部署栈均执行成功），无需换镜像
- [x] 1.2 `schema-postgres.sql` 增加 `CREATE EXTENSION IF NOT EXISTS vector;` 与 `sys_embedding_config`/`kb_doc`/`kb_chunk` 三表（列含 embedding vector 无维度、doc_id 索引）；验证：存量 vben5 库执行 upgrade SQL 后三表建成
- [x] 1.3 新增 `db/upgrade-20261009-kb-rag-postgres.sql`（扩展 + 三表 + 菜单/权限码 INSERT，与 DatabaseSeeder 一致）；验证：对存量 vben5 库手动执行后菜单出现（/system/embedding、/system/kb）

## 2. Embedding 配置（后端）

- [x] 2.1 `module/ai/emb/` 实体 + mapper（mapper 子包）+ `SysEmbeddingConfigService`（CRUD/脱敏 maskKey/激活互斥/名称唯一/校验），错误码 `error.ai.emb.*` 写入 messages*.properties 中英双语
- [x] 2.2 `EmbeddingClient`（JDK HttpClient，POST /embeddings，批量 ≤16，401/404/429 等映射可读中文错误，返回向量列表与维度）；单测 9 用例覆盖 URI 归一化与响应解析
- [x] 2.3 连通测试接口（真实发送样本文本，返回维度 + 耗时并回填 dimension 字段）+ EmbeddingConfigController；E2E 实测：新增→测试（dimension=8）→激活→列表脱敏回显
- [x] 2.4 单测激活互斥与留空不覆盖 Key（`SysEmbeddingConfigServiceTest` 11 用例）

## 3. 知识库管线（后端）

- [x] 3.1 `module/ai/kb/` 实体（KbDoc/KbChunk）+ mapper 子包；MyBatis 注解 SQL：按 `embedding <=> CAST(#{v} AS vector)` 余弦 Top-K 检索
- [x] 3.2 切块器 KbTextChunker：md 按标题层级切、段落/800 字符兜底、~80 字符重叠；纯文本按段落聚合；单测 7 用例覆盖 md/纯文本/超长段落/重叠
- [x] 3.3 异步摄取管线 `KbIngestService`（单 worker @Async 线程池）：受理 → 切块 → 批量向量化 → 落库 → 状态流转；单测 4 用例（成功/空内容/上游失败清理/落库失败清理）
- [x] 3.4 KbController：上传（md/txt 白名单 + 10MB + @Idempotent/@RateLimit）、分页列表（文件名模糊）、删除（处理中拒删、级联删 chunk、@OperLog）、检索测试接口；E2E 实测上传 md → 6 块完成 → 检索命中
- [x] 3.5 维度一致性校验：检索前比对查询向量与库中向量维度，不一致返回可读错误（单测覆盖）

## 4. AI 工具接入

- [x] 4.1 `AiKnowledgeTools.search_knowledge`（@AiAgentTool QUERY、无权限码、Top-4、单块 500 字符截断、总回喂 3000 字符上限、未命中/失败返回状态说明）；E2E：AI 对话「如何启动后端服务」回答明确引用《vben-deploy-guide.md》
- [x] 4.2 `AiSystemTools.MENU_TITLE_ZH` 同步「Embedding 配置」「知识库」两条菜单名

## 5. 前端

- [x] 5.1 `api/system/embedding.ts` + `api/system/kb.ts`（对接新 REST；与 llm.ts 同目录惯例）
- [x] 5.2 `views/system/embedding/` 页面（useVbenVxeGrid 列表 + useVbenModal 表单 Key 密码框留空不改 + 连通测试 + 激活二次确认，v-access:code 按钮权限）；浏览器验证
- [x] 5.3 `views/system/kb/` 页面（文档列表状态列/失败原因、ElUpload http-request 上传限 md/txt、删除二次确认、检索测试弹窗展示片段与得分）；浏览器验证：上传→状态完成→检索 4 命中（文件名/块序/相似度）
- [x] 5.4 locales 中英同构 `page.system.embedding`/`page.system.kb` 与全部按钮文案；`pnpm check:type` 通过

## 6. 收尾

- [x] 6.1 后端 `mvn test` 全绿（24 类 166 用例）；前端 `pnpm check:type` 通过
- [x] 6.2 根 README「项目亮点/功能清单/注意事项」同步（RAG 关键词、pgvector 注意事项、维度重建提示）
- [x] 6.3 E2E：浏览器登录 → 上传文档 → 摄取完成 6 块 → 检索测试命中 → AI 对话问文档内容回答带出处；空库/未命中「如实告知」由 AiSkillToolsTest 与 KbSearchServiceTest 单测覆盖
- [x] 6.4 归档 openspec 变更（sync delta → archive）：`openspec validate kb-rag-mvp --strict` 通过后 `openspec archive kb-rag-mvp --yes`
