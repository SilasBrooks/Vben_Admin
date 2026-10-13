# kb-rerank-citation-jump

## Why

混合检索（pgvector 余弦 + PG 全文 RRF 融合）解决了「单通道召回偏差」，但 RRF 只是按排名投票的粗排序：双通道各取 Top-50 融合后的候选顺序仍可能与真实语义相关性不一致（如精确关键词命中但语义平庸的片段排在语义高度相关片段之前）。业界标准做法是在融合召回之后加一层 **Rerank 精排**（Cross-Encoder 逐对打分），显著提升 Top-K 质量。

同时，AI 对话溯源卡片目前只能「展开摘要」，用户看到答案依据后无法回到文档原文语境核对上下文——溯源链路断在卡片。需要打通「溯源卡片 → 文档原文定位」最后一步。

## What Changes

1. **Rerank 精排（可插拔）**：检索链路升级为「召回 → RRF 融合 → 精排」三层。新增 Rerank 模型配置（OpenAI 兼容 `/rerank` 协议，如硅基流动 BGE-reranker、Jina），全局唯一激活；未配置/未激活时检索行为与现状完全一致（纯 RRF），配置后召回候选放宽再精排取 Top-K。精排上游失败时降级返回 RRF 顺序（可用性优先，不阻断 AI 问答）。
2. **溯源卡片跳转原文**：citations 数据链路（工具回喂 JSON → SSE → 前端）增加 docId；新增文档原文预览接口（kb_chunk 按 seq 排序即正文，支持按块定位）；溯源卡片新增「查看原文」入口，弹窗展示按块分隔的文档全文并高亮定位到引用块；citations 去重 key 由 `docName#seq` 修正为 `docId#seq`（同名文档不再误合并）。

## Capabilities

### 新增

- `system/rerank-config`：Rerank 精排模型配置能力（CRUD / 激活切换 / 连通测试，安全模式与嵌入配置一致）

### 修改

- `ai/knowledge-base`：MODIFIED「混合检索」——增加精排层（可插拔、降级语义、rerankScore 字段）
- `ai/assistant`：MODIFIED「知识库引用溯源卡片」——citations 增加 docId、溯源卡片跳转原文预览、去重 key 修正

## Impact

- **后端**：新增 `module/ai/rerank/` 配置模块；`KbSearchService` 精排链路；`KbController` 预览接口；`AiKnowledgeTools` / `AiChatService` citations 增加 docId；`sys_rerank_config` 建表 + 菜单权限码 SQL（DatabaseSeeder + 存量 SQL 同步）
- **前端**：新增「精排配置」管理页；溯源卡片查看原文弹窗；检索测试弹窗精排分展示；locales 中英同步
- **兼容性**：未配置 Rerank 时所有现有行为不变（纯加法升级）；存量 citations 消息无 docId 时「查看原文」入口隐藏
