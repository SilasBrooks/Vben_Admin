# ai/assistant Specification（Delta）——底层模型可插拔

## MODIFIED Requirements

### Requirement: 流式对话协议

前端 MUST 通过 `POST /api/ai/chat` 与后端通信，后端 MUST 以 SSE（Server-Sent Events）返回，事件类型 MUST 为固定四种：`delta`（文本片段）、`toolcall`（需要确认的写操作）、`done`（本轮结束）、`error`（错误提示）。模型接口凭据（API Key、接口地址、模型名）MUST 仅保存在后端，任何接口响应 MUST NOT 包含 Key 明文。

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

### Requirement: 上下文记忆与持久化

系统 MUST 对会话上下文实施窗口管理：窗口按完整对话轮次（以 user 消息为轮次起点）截断，MUST NOT 产生孤立的 tool 或 assistant(tool_calls) 消息导致上游协议错误。窗口默认保留最近 50 条协议消息。

被移出窗口的历史轮次 MUST 经服务端摘要接口（`POST /api/ai/chat/summarize`，由当前激活的大模型生成）与已有摘要合并为一份「此前对话摘要」，以 system 消息随每次对话请求注入。摘要过程 MUST 对用户透明；摘要失败时 MUST 降级为直接截断并保证当前对话继续，MUST NOT 阻断提问。

会话（消息、摘要、覆盖进度、确认卡片状态）MUST 按用户隔离持久化于浏览器本地存储，刷新页面后恢复；用户清空会话时 MUST 同步清除本地存储。服务端 MUST NOT 存储会话状态。

#### Scenario: 超窗按轮截断保证协议配对

- **WHEN** 历史消息数超过窗口上限且切断点落在一轮工具对话的中间
- **THEN** 窗口起点向后推进到最近一条 user 消息，被跳过的碎片仅进入摘要载荷，上游不返回协议错误

#### Scenario: 旧轮次滚动摘要注入

- **WHEN** 有历史轮次被移出窗口
- **THEN** 系统将其与已有摘要合并生成新摘要，并作为 system 消息随后续每次请求注入，用户无感知

#### Scenario: 摘要失败降级

- **WHEN** 摘要接口调用失败（余额不足/网络异常等）
- **THEN** 当前提问照常发送与回答，旧轮次被直接截断，不出现报错气泡

#### Scenario: 刷新页面后会话恢复

- **WHEN** 同一用户刷新页面后重新打开助手
- **THEN** 历史消息、摘要与确认卡片状态恢复，可继续此前话题；不同用户的本地会话互不可见

#### Scenario: 清空会话清除本地存储

- **WHEN** 用户点击清空会话
- **THEN** 界面消息与本地存储同时清空，下次打开为全新会话
