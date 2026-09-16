# 任务清单：add-ai-context-memory

- [x] 1.1 后端 `AiChatService.summarize(priorSummary, messages)`：摘要系统提示词 + 消息渲染（tool 结果 120 字/消息 500 字/最多 80 条截断）+ 复用 streamChat 聚合文本，空结果抛 AiUpstreamException
- [x] 1.2 后端 `AiController`：`POST /ai/chat/summarize`（SummarizeDto{priorSummary, messages} → SummarizeVo{summary}），同步返回，登录即可（附带：服务端 sanitize 兜底上限 20→60）
- [x] 1.3 前端 `context-window.ts` 纯函数：buildContextWindow(list, covered, max=50) → {window, dropped, covered}，切断点向后找 user 消息，类型自包含
- [x] 1.4 前端 `api/ai/chat.ts`：summarizeAiChatApi（role 'system' 类型已预留，无需扩展）
- [x] 1.5 前端 `use-ai-chat.ts`：发送前超窗摘要（失败降级/并发跳过）、摘要 system 消息注入、localStorage 持久化（按 userId 隔离、轮次结束写入、500 条上限裁剪平移 covered、恢复续号 messageIdSeed）、clear 同步清存储
- [x] 1.6 `mvn compile` + `vue-tsc` 通过
- [x] 2.1 curl 验证摘要接口：priorSummary + 4 条消息 → 返回中文合并摘要（关键事实齐全）；未登录 401
- [x] 2.2 窗口算法用例验证（node 原生类型剥离跑真实 TS 模块，8/8 通过）：正常截断 / 切断点在 tool 消息中间向后推进 / 孤立 tool 断言 / covered 续算（超窗+未超窗）/ 短列表 / 病态输入越界钳制
- [x] 2.3 浏览器冒烟：发消息 → 刷新 → 历史恢复 ✓；追问领域内上下文（"刚才查的第一个角色"）AI 正确答出"超级管理员" ✓；清空 → 存储清除 ✓（注：记忆暗号类问题被领域范围规则正常拒答，非上下文缺陷）
- [x] 2.4 同步主 spec（ADDED 上下文记忆与持久化）→ 归档变更
