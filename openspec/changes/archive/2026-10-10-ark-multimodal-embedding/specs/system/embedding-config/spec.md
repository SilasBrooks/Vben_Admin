## MODIFIED Requirements

### Requirement: Embedding 模型配置管理

系统 SHALL 提供嵌入模型配置管理接口（`/system/embedding`）：分页查询（System:Emb:List）、新增（System:Emb:Add，幂等 + 限流）、编辑（System:Emb:Edit）、删除（System:Emb:Delete，审计）、激活（System:Emb:Activate，幂等 + 审计）。每条配置 SHALL 包含名称、接口地址 baseUrl、API Key、模型名（如 `bge-m3`、`text-embedding-3-small`）、协议风格 protocol、向量维度与启停状态；配置名 MUST 全局唯一。协议风格 MUST 支持两种取值：`standard`（OpenAI 兼容协议，请求发往 `{baseUrl}/embeddings`，纯文本数组入参，默认值）与 `ark-multimodal`（方舟多模态协议，请求发往 `{baseUrl}/embeddings/multimodal`，文本包装为 `[{"type":"text","text":...}]` 结构入参）；protocol 取值 MUST 校验，非法值拒绝保存。系统 SHALL 对两种协议返回的向量数据兼容解析（`data` 为数组或单对象形态均可），知识库摄取与检索 MUST 复用激活配置的协议且无需感知差异。新增与编辑时系统 SHALL 提供连通测试（System:Emb:Edit，限流）：真实发送一条样本文本向量化请求，返回向量维度与耗时；维度 MUST 以实测返回值回填。

#### Scenario: 名称重复拒绝
- **WHEN** 新增配置时提交已存在的名称
- **THEN** 返回 400 业务错误，不创建

#### Scenario: 连通测试回填维度
- **WHEN** 用户对一条配置执行连通测试且上游返回 1536 维向量
- **THEN** 接口返回实测维度 1536 与耗时，保存后该配置的维度字段更新为 1536

#### Scenario: 多模态协议连通测试
- **WHEN** 用户将协议风格选为 ark-multimodal 并执行连通测试
- **THEN** 请求发往 `{baseUrl}/embeddings/multimodal` 且文本以结构化入参发送，测试返回实测维度与耗时

#### Scenario: 非法协议值拒绝
- **WHEN** 新增配置时 protocol 提交为 `grpc`（不在支持列表）
- **THEN** 返回业务错误，不创建

#### Scenario: 存量配置默认标准协议
- **WHEN** 存量库执行升级 SQL 后查看既有配置
- **THEN** 全部配置 protocol 为 `standard`，连通测试与向量化行为与升级前一致
