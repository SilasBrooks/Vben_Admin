# Design: kb-rerank-citation-jump

## Context

混合检索已上线：`KbSearchService.search()` → `KbChunkMapper.hybridSearch()`（两路各 Top-50 RRF 融合）→ `KbSearchHit{score, vecScore, matchType}`。溯源链路：`AiKnowledgeTools.search_knowledge` 回喂 JSON snippets（docName/seq/score/content）→ `AiChatService.parseCitations` → SSE `citations` 事件 → 前端 `mergeCitations`（`docName#seq` 去重）→ `AiAssistant.vue` 卡片（展开摘要，无跳转）。

文档正文分布：`kb_doc` 无 content 列（旧路径正文切块入库后即弃）；`kb_chunk.content` 持有每块正文，按 `doc_id + seq` 排序即文档正文序列（含少量块间重叠字符，属切块语义，预览场景可接受且天然支持按 seq 定位）。

配置能力先例：`module/ai/emb/`（sys_embedding_config 表、Key 明文入库 + 脱敏回显 + 编辑留空不改、全局唯一激活、连通测试回传真实数据、无 yml 兜底）。

## Goals / Non-Goals

**Goals**

- 检索质量：融合候选经 Cross-Encoder 精排后返回 Top-K
- 可插拔：未配置 Rerank 时零行为差异；配置后即时生效（每请求解析激活配置）
- 可用性：精排上游故障时降级 RRF 结果，AI 问答不中断
- 溯源闭环：卡片 → 原文弹窗 → 引用块高亮定位

**Non-Goals**

- 不做 rerank 结果缓存（MVP 规模无需）
- 不做多配置并行打分对比
- 不做原文编辑/标注，预览只读
- 不读存储文件还原原始排版（kb_chunk 序列即预览数据源，避免 pdf/docx 二次解析）

## Decisions

### D1：Rerank 配置存独立新表 sys_rerank_config，独立菜单页

与嵌入配置平级独立（协议不同：`/rerank` vs `/embeddings`；生命周期不同：rerank 非必需、可后配）。安全模式照搬：Key 明文入库、回显脱敏、编辑留空不改 Key、`is_active` 全局唯一事务保证。菜单挂「系统管理」下，权限码 `System:Rerank:List/Add/Edit/Delete/Activate`。同步三处：DatabaseSeeder、存量库 upgrade SQL、`AiSystemTools.MENU_TITLE_ZH`。

### D2：RerankClient 走 OpenAI 兼容 `POST {base}/rerank`

请求 `{model, query, documents: string[], top_n}`，响应 `{results: [{index, relevance_score}]}`（硅基流动/Jina/Cohere 兼容形态均含此结构）。按 index 回填到候选对象，`relevance_score` 写入 `KbSearchHit.rerankScore`。超时复用配置的 `timeout_seconds`。

### D3：精排链路与降级语义（可用性优先）

- 召回宽度：配置激活时 `hybridSearch` 外层 LIMIT 放宽为 `max(topK * 4, 16)`（上限 30），未配置时保持 `topK`（SQL 不变，仅参数差异）
- 精排失败（超时/4xx/5xx/解析异常）：`RerankClient` 捕获后 `log.warn` 返回 null，`KbSearchService` 降级按 RRF 顺序返回（rerankScore 全 null）——**不抛异常**，保证 AI 主链路永不因精排中断
- 前端感知：命中项 `rerankScore` 有值显示「精排分」，无值显示「融合分」（与现状一致）；不在接口层额外暴露降级标记（MVP 从得分字段可判别）

### D4：原文预览接口仅要求登录，数据源 kb_chunk

`GET /system/kb/doc/{id}/preview` 不加 `@RequirePermission`——与 `search_knowledge` 无权限码同语义（知识库面向全体登录用户，溯源卡片对全体可见，跳转原文应同权）。返回 `{fileName, blocks: [{seq, content}]}`（kb_chunk 按 seq 升序全量）。文档不存在返回业务错误；文档未完成/无块时返回空 blocks（前端提示无正文）。限流 `@RateLimit` 防遍历。

### D5：citations 链路加 docId，去重 key 改 `docId#seq`

- `AiKnowledgeTools` snippets 增加 `docId`（回喂模型的 JSON 同步携带，模型可引用但提示词不要求）
- `KbCitation` 增加 `docId` → SSE 事件 → 前端类型
- 去重 key `docName#seq` → `docId#seq`：同名不同文档（秒传场景常见）不再误合并；旧会话内存数据无迁移问题（仅本轮会话生命周期）
- 卡片「查看原文」入口仅在有 `docId` 时渲染（兼容刷新恢复后的存量消息）

### D6：预览弹窗为 AI 助手目录下的独立组件

`components/ai-assistant/CitationDocPreview.vue`：useVbenModal 弹窗，左侧文档名标题 + 右侧按块分隔正文流；打开时按引用 seq 滚动定位并高亮该块（一次性动画）。检索测试弹窗不加跳转（本次范围仅溯源卡片）。

## Risks / Trade-offs

- **精排增加延迟**：一次上游调用（候选 ≤30 条文本）。→ top_n 即 Top-K、候选收窄上限 30、超时兜底降级，最坏情况退化为现状
- **rerankScore 与 rrfScore 量纲混淆**：两分并存展示时明确标签（精排分/融合分），排序依据为最终返回顺序（精排后按 relevance_score 降序）
- **kb_chunk 正文与原始排版有差异**（重叠字符、无图片表格）：预览定位的是「检索块语境」而非原文件还原，卡片入口文案用「查看原文出处」明确语义
- **preview 无权限码被质疑越权**：kb_chunk 内容本就经检索面向全体登录用户暴露（AI 回答含原文片段），预览未扩大暴露面；接口有限流与登录态校验

## Migration Plan

1. SQL：schema-postgres.sql 增 sys_rerank_config 建表 + 菜单权限码；存量库 upgrade SQL 同步（幂等 IF NOT EXISTS）
2. 存量数据无需迁移（rerank 配置从零开始；kb_chunk 已有数据直接可预览）
3. 未配置 rerank 前全链路行为与现状一致，可安全灰度

## Open Questions

- 无
