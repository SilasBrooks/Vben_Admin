## Purpose

提供 Embedding（向量化）模型配置管理：与对话模型配置解耦的独立配置能力，供知识库切块向量化与检索使用。覆盖配置 CRUD、全局唯一激活、连通测试与 Key 脱敏，安全约定与对话模型配置完全对齐。

## ADDED Requirements

### Requirement: Embedding 模型配置管理

系统 SHALL 提供嵌入模型配置管理接口（`/system/embedding`）：分页查询（System:Emb:List）、新增（System:Emb:Add，幂等 + 限流）、编辑（System:Emb:Edit）、删除（System:Emb:Delete，审计）、激活（System:Emb:Activate，幂等 + 审计）。每条配置 SHALL 包含名称、接口地址 baseUrl、API Key、模型名（如 `bge-m3`、`text-embedding-3-small`）、向量维度与启停状态；配置名 MUST 全局唯一。协议范围限定为 OpenAI 兼容协议（`/embeddings`）。新增与编辑时系统 SHALL 提供连通测试（System:Emb:Edit，限流）：真实发送一条样本文本向量化请求，返回向量维度与耗时；维度 MUST 以实测返回值回填。

#### Scenario: 名称重复拒绝
- **WHEN** 新增配置时提交已存在的名称
- **THEN** 返回 400 业务错误，不创建

#### Scenario: 连通测试回填维度
- **WHEN** 用户对一条配置执行连通测试且上游返回 1536 维向量
- **THEN** 接口返回实测维度 1536 与耗时，保存后该配置的维度字段更新为 1536

### Requirement: API Key 回显脱敏与编辑留空不覆盖

配置列表与编辑回显 MUST 对 API Key 脱敏（与对话模型配置同一规则），任何接口响应 MUST NOT 返回 Key 明文；编辑时 Key 输入留空 MUST 表示保持原值不变。Key 仅在服务端向量化调用时使用，MUST NOT 进入 git 或日志。

#### Scenario: 列表回显脱敏
- **WHEN** 具备 System:Emb:List 权限的用户查看配置列表
- **THEN** apiKey 列显示脱敏值（如 `sk-in****3456`），无明文

#### Scenario: 编辑留空不覆盖 Key
- **WHEN** 编辑配置时 API Key 留空提交
- **THEN** 库中原 Key 保持不变，回显仍为原脱敏值

### Requirement: 全局唯一激活

系统 SHALL 提供激活接口：全局同时 MUST 仅存在一条激活配置（事务内全表清零后置目标行）。停用（enabled=0）的配置 MUST 拒绝激活；激活中的配置 MUST 拒绝删除。激活后向量化调用 MUST 无需重启即时生效；上游不可用时 MUST 返回可读中文错误，MUST NOT 静默降级到其他配置或本地兜底向量。

#### Scenario: 激活互斥
- **WHEN** 先激活配置 A 再激活配置 B
- **THEN** 仅 B 处于激活状态，A 自动解除

#### Scenario: 停用配置不可激活
- **WHEN** 对 enabled=0 的配置调用激活
- **THEN** 返回业务错误，激活状态不变

### Requirement: 前端嵌入配置页面

前端 SHALL 在「系统管理」下提供嵌入模型配置页面（`/system/embedding`）：列表（名称/模型/接口地址/脱敏 Key/维度/启停/激活状态）+ 新增/编辑弹窗（Key 密码框，编辑时提示"留空则不修改"）+ 连通测试 + 激活（二次确认）+ 删除（激活中禁用），操作按钮以 System:Emb:Add/Edit/Delete/Activate 权限码控制。

#### Scenario: 无权限按钮隐藏
- **WHEN** 无 System:Emb:Activate 权限的账号进入该页面
- **THEN** 不渲染激活按钮
