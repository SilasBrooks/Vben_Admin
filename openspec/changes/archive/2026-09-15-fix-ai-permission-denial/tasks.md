# 任务清单：fix-ai-permission-denial

- [x] 1.1 `AiChatService.chat()`：CREATE 类工具下发 toolcall 前校验 `loginUser.hasPermission(def.permission())`；无权限改为追加 role=tool 的"当前账号没有「X」的权限"结果并继续本轮（不发卡片、不落库）
- [x] 1.2 SYSTEM_PROMPT 增加规则：工具返回权限不足时直接告知用户"你的权限不足"并说明缺少的权限，不要重复尝试
- [x] 1.3 `mvn compile` 通过
- [x] 2.1 E2E：stockAdmin（无 System:User:Add/System:User:List）SSE 对话"帮我创建一个用户"→ 模型答复权限不足、无 toolcall 事件、sys_user 无新增；直连 execute 仍 403（纵深防御）
- [x] 2.2 同步主 spec（MODIFIED 服务端权限校验）→ 归档变更
