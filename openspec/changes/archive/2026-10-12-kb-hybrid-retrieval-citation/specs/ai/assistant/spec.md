# ai/assistant delta

## ADDED Requirements

### Requirement: 知识库引用溯源卡片

当本轮对话执行 `search_knowledge` 工具且检索命中时，后端 MUST 通过 SSE 事件 `citations` 下发结构化出处列表，每条出处 SHALL 包含文档名（docName）、块序号（seq）、融合得分（score）与内容摘要（content，与回喂模型的片段一致并同样裁剪）。一次对话中出现多次命中 SHALL 逐次下发或合并下发，前端 MUST 按文档名 + 块序号去重后在对应助手气泡下方渲染溯源卡片。

溯源卡片 MUST 展示文档名、块序号与得分，默认收起内容摘要（可展开查看完整摘要文本）；卡片数量上限与回喂片段数一致（Top-K）。卡片渲染 MUST 与 Markdown 正文互不干扰，且 MUST NOT 引入 HTML 注入风险（摘要按纯文本展示）。

前端卡片数据仅存在于本轮会话内存（与消息气泡同生命周期），刷新页面后随会话恢复流程一并丢失属于可接受行为。

#### Scenario: 命中后展示溯源卡片

- **WHEN** 用户问「系统的接入流程是什么」，模型调用 search_knowledge 命中 2 个片段
- **THEN** 回答气泡下方出现 2 张溯源卡片，展示文档名、块序号与得分，点击可展开内容摘要

#### Scenario: 重复命中去重

- **WHEN** 同一轮对话模型先后两次调用 search_knowledge，两次结果含相同文档与块序号的片段
- **THEN** 气泡下的溯源卡片按文档名 + 块序号去重，不出现重复卡片

#### Scenario: 未命中不出卡片

- **WHEN** search_knowledge 返回未命中状态说明
- **THEN** 不下发 citations 事件，气泡下无溯源卡片

## MODIFIED Requirements

### Requirement: 流式对话协议

前端 MUST 通过 `POST /api/ai/chat` 与后端通信，后端 MUST 以 SSE（Server-Sent Events）返回，事件类型 MUST 为固定五种：`delta`（文本片段）、`toolcall`（需要确认的写操作）、`citations`（知识库检索出处，见「知识库引用溯源卡片」）、`done`（本轮结束）、`error`（错误提示）。模型接口凭据（API Key、接口地址、模型名）MUST 仅保存在后端，任何接口响应 MUST NOT 包含 Key 明文。

后端 MUST 按以下顺序解析每次对话使用的底层大模型（OpenAI 兼容协议）：

1. `sys_llm_config` 中 `is_active=1 AND enabled=1` 的唯一激活配置；
2. 未命中激活配置时，回退 yml `deepseek.*` 兜底配置。

解析 MUST 在每次对话开始时执行一次并在整次对话（含多轮工具调用）内固定；配置新增、编辑或激活后 MUST 无需重启即时生效。激活配置不可用（连接失败/认证失败）时 MUST 返回可读中文错误，MUST NOT 静默切换到兜底配置或其他配置。

前端 MUST 使用 fetch + ReadableStream 读取流，并在发起新请求或清空会话时中断上一条未完成的流，避免多条流并发写同一会话。

#### Scenario: 普通问答流式输出

- **WHEN** 用户发送与系统操作无关的问题
- **THEN** 回复文字以打字机方式逐段出现在助手气泡中，结束后收到 done 事件

#### Scenario: 对话使用激活配置

- **WHEN** 管理员在模型配置页将激活配置从 A 切换为 B
- **THEN** 此后的 AI 对话 MUST 使用 B 配置的接口地址、Key 与模型发起请求，全程无需重启服务

#### Scenario: AI 服务未配置

- **WHEN** 后端既无激活模型配置、yml 兜底也未配置 API Key
- **THEN** 前端收到 error 事件并在气泡中显示"AI 服务未配置"类提示（引导至系统管理-模型配置），不抛出未捕获异常

#### Scenario: 激活配置不可用不静默降级

- **WHEN** 激活配置的接口地址无法连接
- **THEN** 前端收到 error 事件并显示「AI 服务暂时不可用」类可读错误，系统 MUST NOT 静默改用兜底配置应答

### Requirement: 知识检索工具

系统 MUST 提供 `search_knowledge` 查询类 AI 工具（无需权限码，登录即可调用，与 `query_my_notices` 同语义）：参数为用户问题文本，后端执行知识库混合检索（向量 + 全文 RRF 融合）并回喂模型 Top-K 片段（文档名、块序号、正文）。回喂结果 MUST 经过长度裁剪防止上下文爆炸；知识库为空、未命中或检索失败时 MUST 返回明确状态说明（如「知识库中未找到相关内容」），MUST NOT 让模型在无依据时编造文档内容。

工具命中时 MUST 同步通过 `citations` SSE 事件向前端下发出处列表（见「知识库引用溯源卡片」），回喂模型的片段与下发给前端的出处 SHALL 同源一致。

工具说明 MUST 引导模型：当用户询问业务操作、制度规范、项目文档类问题而系统工具无法回答时调用本工具；回答基于检索结果时 MUST 注明出处（文档名），知识库未命中时如实告知并建议用户补充文档或换用系统操作能力。

#### Scenario: 知识类问题自动检索

- **WHEN** 用户问「系统的接入流程是什么」且知识库已收录接入指南
- **THEN** 模型调用 search_knowledge，后端自动执行检索回喂片段，全程不出现确认卡片，回答注明「根据《接入指南》」，同时前端收到 citations 事件展示溯源卡片

#### Scenario: 未命中如实告知

- **WHEN** 用户问的问题在知识库中无相关内容
- **THEN** 模型基于「未找到相关内容」的状态说明如实回答，不编造文档依据，不下发 citations

#### Scenario: 检索失败不阻断

- **WHEN** 向量化上游不可用导致检索失败
- **THEN** 工具返回可读失败说明，模型告知用户检索暂不可用，本轮对话继续而非报错中断
