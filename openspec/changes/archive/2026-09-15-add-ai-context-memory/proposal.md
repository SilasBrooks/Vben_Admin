# 变更提案：add-ai-context-memory

## Why

当前 AI 助手会话记忆只有前端硬编码 `slice(-20)` 的滑动窗口，存在三个问题（2026-09-15 用户提出）：

1. **协议 400 隐患**：按条数硬切可能把一轮工具对话从中间切断（孤立 tool 消息），触发 DeepSeek 协议错误
2. **硬遗忘**：约 10 轮普通问答 / 4~6 轮工具对话后，早期内容对模型彻底不可见，且无摘要补偿
3. **刷新即失忆**：历史仅存组件内存，页面刷新全部丢失

## What Changes

- **按轮截断**：窗口从最后一条 user 消息的完整轮次边界截断，杜绝孤立 tool/assistant(tool_calls) 消息
- **滚动摘要**：历史超窗时，被移出的旧轮次经 `POST /ai/chat/summarize`（DeepSeek）与已有摘要合并为「此前对话摘要」，以 system 消息随每次请求注入；摘要失败降级为直接截断，不阻断对话
- **本地持久化**：消息、摘要、覆盖进度按用户隔离存 localStorage，刷新后恢复（含确认卡片状态）；清空会话同步清存储

## Capabilities

### Modified Capabilities

- `ai/assistant`: 新增「上下文记忆与持久化」需求（窗口构建规则、摘要注入、降级、刷新恢复、清空）

## Impact

- **后端**：`AiController` + `AiChatService` 新增同步摘要接口（复用 DeepSeekClient 流式客户端聚合文本），约 80 行；无表结构/协议破坏性变更
- **前端**：`use-ai-chat.ts` 窗口构建改造 + 持久化；新增纯函数模块 `context-window.ts`（可独立测试）；`api/ai/chat.ts` 新增摘要 API
- **成本**：仅在超窗时触发一次摘要调用（约每 25 轮问答一次），增加该次发送 2~5 秒延迟
