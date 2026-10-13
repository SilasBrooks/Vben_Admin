# ai/knowledge-base Delta

## MODIFIED Requirements

### Requirement: 混合检索

系统 SHALL 提供混合检索接口（Kb:Doc:List）：输入查询文本，并行执行两路召回——①向量通道：经激活的 Embedding 配置向量化后按 pgvector 余弦距离取 Top-K；②全文通道：查询文本经应用侧分词后按 tsvector GIN 索引取 Top-K（仅命中完成状态文档）。两路结果 SHALL 按 RRF（Reciprocal Rank Fusion）融合排序，每条 SHALL 包含文档名、文档 id、块序号、正文、融合得分（rrfScore，越大越相关）、向量相似度（vecScore = 1 - 余弦距离）与匹配通道（matchType：`vector` 仅向量命中 / `fts` 仅全文命中 / `both` 双通道命中）。查询维度与库中向量维度 MUST NOT 一致性冲突（不一致时返回可读错误而非脏结果）。查询文本无有效分词 token（纯符号等）时 SHALL 退化为纯向量检索。知识库为空或检索无命中时 SHALL 返回空列表而非错误。

系统 SHALL 在 RRF 融合之后支持可插拔的 Rerank 精排层：当存在激活的 Rerank 配置时，SHALL 将融合候选放宽召回（候选数不低于最终 Top-K 的 4 倍且不超过 30 条）后调用激活的 Rerank 模型对「查询-候选」逐对打分，按相关性得分降序返回 Top-K，每条 SHALL 附带精排得分（rerankScore）；当未配置或未激活 Rerank 时 SHALL 直接返回 RRF 融合 Top-K（rerankScore 为空），行为与无精排层完全一致。精排上游调用失败（超时/网络/上游错误）时 MUST 降级返回 RRF 融合顺序（记录告警日志，rerankScore 为空），MUST NOT 阻断或报错中断检索与 AI 问答链路。返回结果排序依据：有精排时按精排得分降序，无精排时按 RRF 融合分降序。

#### Scenario: 精确关键词全文通道召回

- **WHEN** 库中已有「接入指南」文档，以其中精确出现的专有词「Vben-Admin-Deploy」检索
- **THEN** 全文通道命中相关段落并在结果中标记 matchType 含 `fts`，即使语义相似度不高也能进入 Top-K

#### Scenario: 双通道融合排序

- **WHEN** 某查询的向量 Top-K 与全文 Top-K 均命中同一片段
- **THEN** 该片段 matchType 为 `both`，RRF 双份加分后在结果中排名提升

#### Scenario: 融合得分与通道字段返回

- **WHEN** 执行任意混合检索
- **THEN** 每条命中包含 rrfScore、vecScore、matchType 字段，结果按 rrfScore 降序

#### Scenario: 纯符号查询退化

- **WHEN** 查询文本为「!!!」等无有效 token 的内容
- **THEN** 执行纯向量检索正常返回，不因空 tsquery 报错

#### Scenario: 空知识库返回空结果

- **WHEN** 未上传任何文档时执行检索
- **THEN** 返回空列表，不报错

#### Scenario: 激活精排后重排序

- **WHEN** 已激活 Rerank 配置，RRF 融合第 1 名的候选经精排模型打分后低于第 5 名候选
- **THEN** 返回 Top-K 中第 5 名候选排到第 1 名且携带 rerankScore，原第 1 名名次下降或落选

#### Scenario: 未配置精排行为不变

- **WHEN** 系统无激活的 Rerank 配置时执行检索
- **THEN** 直接返回 RRF 融合 Top-K（各条 rerankScore 为空），与精排层引入前的行为一致

#### Scenario: 精排上游故障降级

- **WHEN** 已激活 Rerank 配置但上游接口超时或返回错误时执行检索
- **THEN** 记录告警日志并降级返回 RRF 融合 Top-K（rerankScore 为空），检索与 AI 问答不中断、不报错

## ADDED Requirements

### Requirement: 知识检索工具结果含文档定位

`search_knowledge` 工具回喂模型的 JSON 与溯源卡片下发数据 SHALL 在每条片段中包含文档 id（docId），供前端溯源卡片跳转文档原文预览使用；docId MUST 与检索命中的真实文档一致。

#### Scenario: 片段携带文档 id

- **WHEN** search_knowledge 检索命中返回 snippets
- **THEN** 每条 snippet 含 docId 字段且与命中文档一致
