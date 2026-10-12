# Tasks: kb-hybrid-retrieval-citation

## 1. 后端数据层

- [x] 1.1 `schema-postgres.sql`：kb_chunk 加 `content_tsv tsvector` 列 + `idx_kb_chunk_fts` GIN 索引 + COMMENT；新建 `upgrade-20261012-kb-hybrid-fts-postgres.sql`（幂等）并在 vben5 执行
- [x] 1.2 `KbTokenizer`：中文 bigram + 英文/数字按词，token 上限 16，空格连接；单测覆盖中英混合/纯符号/长文本
- [x] 1.3 `KbChunkMapper`：`insertChunkText` 同步写 content_tsv；新增 `hybridSearch`（向量+全文 FULL OUTER JOIN RRF 单条 SQL）；新增回填三方法（countMissingTsv / selectMissingTsvBatch / updateTsv）

## 2. 后端检索与管线

- [x] 2.1 `KbSearchService` 混合化：query 向量化 + 分词后调 hybridSearch；空 token 退化纯向量；`KbSearchHit` 加 vecScore/matchType
- [x] 2.2 启动回填：`ApplicationRunner` 分批回填存量块 content_tsv（500/批，失败仅记日志）
- [x] 2.3 管线集成：`KbPipelineExecutor` 切块调用处传入分词结果（写入路径带 content_tsv）

## 3. 对话溯源（后端）

- [x] 3.1 `AiChatSink` 加 `citations(List<KbCitation>)` 方法，`SseChatSink` 实现 `citations` 事件下发；`AiKnowledgeTools` 导出工具名常量
- [x] 3.2 `AiChatService`：QUERY 工具执行后识别 search_knowledge 命中，解析 snippets 组装 KbCitation 下发（含截断后的正文，与回喂一致）
- [x] 3.3 messages 中英新增（如需错误文案）；`KbSearchServiceTest` 适配混合语义 + `AiChatService` citations 下发单测

## 4. 前端

- [x] 4.1 `api/ai/chat.ts`：`citations` 事件类型与 `AiCitationItem`；`use-ai-chat.ts` 流处理器挂载当前 assistant 气泡（docName+seq 去重）
- [x] 4.2 `AiAssistant.vue`：溯源卡片渲染（收起/展开摘要，纯文本展示），locales 中英同步
- [x] 4.3 知识库检索测试弹窗：匹配通道标签 + 融合分 4 位小数展示，locales 同步

## 5. 验证与收尾

- [x] 5.1 后端 `mvn test` 全绿（基线 245 不得破坏）；前端 `pnpm check:type` 通过
- [x] 5.2 E2E（真实后端）：存量回填生效；中文精确词走全文通道命中（matchType=fts/both）；检索测试弹窗展示通道与融合分；对话问知识类问题收到 citations 事件与溯源卡片；纯符号查询退化不报错
- [x] 5.3 README「知识库 / AI 助手」段落更新混合检索与溯源卡片；存量库清单补 upgrade SQL
- [x] 5.4 `openspec validate kb-hybrid-retrieval-citation --strict` 通过 → `openspec archive kb-hybrid-retrieval-citation --yes`（归档前不 commit）
