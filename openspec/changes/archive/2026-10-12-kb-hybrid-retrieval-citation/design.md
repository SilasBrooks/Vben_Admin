# Design: kb-hybrid-retrieval-citation

## Context

- 现状：`KbSearchService.search` 走单条 SQL——`kb_chunk.embedding <=> 查询向量` 余弦 Top-K，返回 `KbSearchHit`（docName/seq/content/score=1-余弦距离）；调用方两处：检索测试接口与 `AiKnowledgeTools.searchKnowledge`（回喂 LLM）
- AI 对话管线：`AiChatService` agent loop 中 QUERY 工具经 `toolExecutor.runQuiet` 自动执行，`AiChatSink` 接口（SseChatSink 实现）已有 delta/toolcall/plan/history/done/error 六类事件下发
- kb_chunk 写入路径：切块阶段 `insertChunkText`（仅正文），向量化阶段 `updateEmbedding` 回填——tsvector 应跟随切块阶段写入
- 约束：SQL 初始化器会截断 `$$...$$` dollar-quoted 函数（AGENTS.md），存量库靠幂等 upgrade SQL + 应用层补偿；PG 18（bitnami）自带 pgvector 但无中文分词扩展

## Goals / Non-Goals

**Goals**

- 双通道召回 + RRF 融合，显著提升专有名词/编号/精确词查询的召回率
- 溯源卡片让对话中的知识类回答可核验
- 全链路不引入新依赖、不要求中文分词扩展

**Non-Goals**

- 不做 Rerank 模型重排（后续独立变更）
- 不做多知识库空间隔离
- 不做 citations 的服务端持久化（刷新丢失，与现有会话内存语义一致）
- 不改动检索测试接口的 URL 与权限（响应体加字段，向前兼容）

## Decisions

### D1：中文分词用应用侧 bigram，不用数据库扩展

zhparser/pg_jieba 需要编译扩展，开发与部署环境（bitnami 镜像）不可移植。改为 `KbTokenizer` 纯 Java：中文相邻两字切分 + 英文/数字按词，token 空格连接后 `to_tsvector('simple', ...)` 入库，查询侧同一分词器 + `plainto_tsquery('simple', ...)`（token 间 AND）。bigram 对中文查询的召回/精度折中在本项目文档量级（单库 ≤ 数万块）下足够，且 2 字词查询天然精确命中。

替代方案（已否决）：装 zhparser（部署不可移植）；PG 默认 simple 整句不分词（中文全文通道失效）；n-gram PG 内置配置无此物。

### D2：tsvector 存列 + GIN，写入挂切块阶段、存量走应用层回填

`kb_chunk.content_tsv tsvector NULL`，GIN 索引。切块写入时同事务带分词结果（`CAST(#{tokens} AS tsvector)` 由 `to_tsvector('simple', tokens)` 生成）。存量回填不进 upgrade SQL（DO $$ 块会被初始化器截断），由 `ApplicationRunner` 启动后分批（500 行/批）回填 `content_tsv IS NULL` 的行，幂等可重跑，失败仅记日志不阻塞启动——与「updateTime 用 MetaObjectHandler 不用触发器」的既有约束同思路。

### D3：RRF 融合在单条 SQL 内完成

`hybridSearch` SQL：向量子查询（`WHERE d.status=1 AND embedding IS NOT NULL`，按距离取 50）与全文子查询（`content_tsv @@ plainto_tsquery(...)`，按 ts_rank 取 50）`FULL OUTER JOIN`，融合分 `1/(60+COALESCE(vec_rank,9999)) + 1/(60+COALESCE(fts_rank,9999))`，按融合分排 LIMIT topK。常数 60 为 RRF 论文标准 k 值。候选池 50 > topK 上限 10，保证融合后有足够裁剪空间。MyBatis 注解 SQL 单条实现，与现有 mapper 风格一致。

### D4：score 语义切换为融合分，附带 vecScore/matchType

`KbSearchHit` 保留 `score`（= rrfScore，排序依据）并新增 `vecScore`、`matchType`（vector/fts/both）。检索测试弹窗得分展示改 4 位小数 + 通道标签。BREAKING 仅体现为数值量级变化，消费方（前端展示、LLM 回喂）均不做数值逻辑判断，兼容风险低。

### D5：citations 从工具回喂 JSON 解析，sink 加一个事件方法

`AiChatService` 在 QUERY 工具自动执行后判断 `search_knowledge` 且结果 `found=true`，反序列化 `AiToolResult.content` 中的 `snippets` 组装 citations 经 `AiChatSink.citations(...)` 下发。工具名用 `AiKnowledgeTools` 导出常量，避免魔法串。替代方案（已否决）：工具执行层 ThreadLocal 收集（跨类隐式传参、异步场景易漏清）；改造工具签名返回结构化对象（波及全部 QUERY 工具协议）。

### D6：前端 citations 挂当前 assistant 气泡，按 docName+seq 去重

`use-ai-chat.ts` 流处理器新增 `onCitations`：合并进当前生成中的 assistant 气泡对象（`bubble.citations`，数组追加去重）；`AiAssistant.vue` 在正文渲染区下方渲染卡片列表（收起态显示文档名/序号/得分，点击展开摘要，摘要纯文本展示防注入）。

## Risks / Trade-offs

- [bigram 索引体积膨胀（每块 token 数 ≈ 2×中文字数）] → GIN 索引压缩 + 单库块量级受 20000 块/文档配额约束；检索延迟以 E2E 实测为准
- [plainto_tsquery 对全部 token 取 AND，长查询可能零命中] → 分词器 token 数上限 16，超限截断；零命中时向量通道仍兜底
- [存量回填在启动时占用少量窗口] → 分批 500 行 + 失败不阻塞；量大时（>10 万块）可后续改为增量分片回填
- [citations 刷新丢失] → Non-Goal 已声明，与现有会话内存语义一致
- [score 数值量级变化引起用户困惑] → 检索测试弹窗同步改「融合分」文案与精度标注

## Migration Plan

1. upgrade SQL（幂等）：kb_chunk 加列 + GIN 索引
2. 部署新后端：启动回填自动补齐存量 tsvector，随后新写入自带分词
3. 前端随发；citations 事件为增量（旧前端忽略未知事件无副作用）
4. 回滚：回滚代码即可，content_tsv 列与索引留存无害

## Open Questions

（无）
