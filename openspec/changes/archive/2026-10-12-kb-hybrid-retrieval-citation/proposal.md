# kb-hybrid-retrieval-citation

## Why

知识库当前仅依赖 pgvector 语义相似检索，对精确关键词、专有名词、编号类查询（语义向量不敏感但字面完全匹配）召回率低；同时 AI 对话回答知识类问题时只在正文里口头提「根据《xx文档》」，缺少可点击、可核验的结构化出处，用户无法快速核对模型是否忠实于知识库。

## What Changes

- **混合检索**：知识库检索从纯向量 Top-K 升级为「向量相似 + PostgreSQL 全文检索」双通道，RRF（Reciprocal Rank Fusion）融合排序；全文通道采用应用侧中文 bigram 分词 + `tsvector` 列 + GIN 索引，不引入数据库扩展
- **存量回填**：kb_chunk 历史数据的 tsvector 由应用启动时批量回填（幂等），与既有「不用数据库触发器」的约束一致
- **score 语义调整**：检索命中项新增融合分（rrfScore）、向量分（vecScore）、匹配通道（matchType：向量/全文/双通道）；检索接口与 AI 工具回喂统一按融合分排序
- **对话引用溯源卡片**：`search_knowledge` 工具执行命中后，对话流通过新 SSE 事件 `citations` 下发结构化出处（文档名、块序号、得分、摘要），前端在助手气泡下方渲染可展开的溯源卡片
- **BREAKING**：检索测试弹窗与 AI 工具回喂中的 score 由「余弦相似度」变为「RRF 融合分」，数值量级变小（约 0.005-0.033）；KbSearchHit JSON 新增字段

## Capabilities

### New Capabilities

（无——全部为既有能力的需求级修改）

### Modified Capabilities

- `ai/knowledge-base`：MODIFIED「向量检索」→「混合检索」；ADDED「全文索引与中文分词」；MODIFIED「前端知识库页面」（检索测试展示匹配通道与融合分）
- `ai/assistant`：MODIFIED「流式对话协议」（SSE 事件新增 `citations`）；MODIFIED「知识检索工具」（混合检索语义 + 命中后下发出处）；ADDED「知识库引用溯源卡片」

## Impact

- **后端**：`db/schema-postgres.sql` 与 upgrade SQL（kb_chunk 加 `content_tsv` 列 + GIN 索引）；`KbChunkMapper`（hybridSearch SQL、写入带分词、回填三方法）；新增 `KbTokenizer`（bigram 分词器）；`KbSearchService`（混合检索编排）；`KbSearchHit`（新字段）；`AiChatService`（search_knowledge 命中后经 `AiChatSink` 下发 citations）；`SseChatSink`
- **前端**：`api/ai/chat.ts`（citations 事件类型与 handler）；`use-ai-chat.ts`（气泡挂载 citations）；`AiAssistant.vue`（溯源卡片渲染）；知识库检索测试弹窗（通道标签与分数精度）；locales 中英文
- **无新增依赖**：分词为纯 Java 实现（中文 2-gram + 英文数字按词），全文检索用 PG 内置 `tsvector`/`plainto_tsquery`，GIN 索引原生支持
