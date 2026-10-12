# ai/knowledge-base delta

## ADDED Requirements

### Requirement: 全文索引与中文分词

系统 SHALL 为知识库块（kb_chunk）维护全文检索索引：块入库（切块阶段）时由应用侧分词器生成分词结果并写入 `content_tsv` tsvector 列，列上 MUST 建立 GIN 索引。分词器 MUST 不依赖任何数据库扩展：中文文本按相邻两字（bigram）切分，英文与数字序列按词切分，全部 token 以空格连接后经 `to_tsvector('simple', ...)` 入库。存量块的分词索引 SHALL 由应用启动时批量回填（幂等：仅处理 `content_tsv IS NULL` 的行，分批执行），回填失败 MUST NOT 阻塞服务启动。检索查询文本 MUST 经同一分词器处理后再参与全文匹配。

#### Scenario: 中文块建立全文索引

- **WHEN** 上传含「系统接入指南」正文的文档并完成向量化
- **THEN** 各块的 content_tsv 非空且包含 bigram 切分 token（如「接入 入系 系统 …」），GIN 索引生效

#### Scenario: 存量数据自动回填

- **WHEN** 对已有块的存量库执行本次升级并重启服务
- **THEN** 应用启动回填后全部块的 content_tsv 均非空，混合检索的全文通道对存量文档生效

#### Scenario: 分词不依赖扩展

- **WHEN** 在未安装任何中文分词扩展的 PostgreSQL 上执行初始化 SQL
- **THEN** 表结构创建成功，分词由应用侧完成，数据库仅执行 to_tsvector('simple', ...)

### Requirement: 混合检索

系统 SHALL 提供混合检索接口（Kb:Doc:List）：输入查询文本，并行执行两路召回——①向量通道：经激活的 Embedding 配置向量化后按 pgvector 余弦距离取 Top-K；②全文通道：查询文本经应用侧分词后按 tsvector GIN 索引取 Top-K（仅命中完成状态文档）。两路结果 SHALL 按 RRF（Reciprocal Rank Fusion）融合排序后返回 Top-K（默认 4，可指定 1-10），每条 SHALL 包含文档名、块序号、正文、融合得分（rrfScore，越大越相关）、向量相似度（vecScore = 1 - 余弦距离）与匹配通道（matchType：`vector` 仅向量命中 / `fts` 仅全文命中 / `both` 双通道命中）。查询维度与库中向量维度 MUST NOT 一致性冲突（不一致时返回可读错误而非脏结果）。查询文本无有效分词 token（纯符号等）时 SHALL 退化为纯向量检索。知识库为空或检索无命中时 SHALL 返回空列表而非错误。

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

## REMOVED Requirements

### Requirement: 向量检索

**Reason**：纯向量检索对中文精确词（专有名词、型号、端口等字面 token）召回能力弱，已由混合检索（pgvector 向量 + PG 全文检索 RRF 融合）取代。

**Migration**：kb_chunk 新增 content_tsv tsvector 列与 GIN 索引，存量块由应用启动时自动回填；检索接口返回字段由单一相似度 score 变更为 rrfScore / vecScore / matchType 三字段（score 语义从余弦相似度切换为 RRF 融合分）。

## MODIFIED Requirements

### Requirement: 前端知识库页面

前端 SHALL 在「系统管理」下提供知识库页面（`/system/kb`）：文档列表（useVbenVxeGrid，状态列带处理中/完成/失败标识，失败可查看原因）+ 上传（ElUpload 托管 http-request 自定义上传，限 md/txt，处理中禁重复上传）+ 删除（二次确认）+ 「检索测试」弹窗（输入问题、展示命中片段、匹配通道标签与融合得分），按钮以 Kb:Doc:Upload/Delete/List 权限码控制。

#### Scenario: 上传后状态轮转

- **WHEN** 用户上传文档后刷新列表
- **THEN** 该行状态从「处理中」随后台完成变为「完成」，块数回填

#### Scenario: 检索测试

- **WHEN** 用户在检索测试弹窗输入问题并提交
- **THEN** 弹窗展示命中片段列表（文档名、内容摘要、匹配通道标签、融合得分），空结果时提示无命中
