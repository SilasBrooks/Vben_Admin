## Context

页面当前能接收后端错误，但后端原先吞掉邮件底层异常。直接记录 MailException 及其文案可能泄露邮箱、正文或服务器认证信息，因此不能通过打印堆栈排查。

## Goals / Non-Goals

- Goals：用户获得可行动的错误提示；管理员可从无敏感信息的日志判断失败阶段和服务商状态码。
- Non-Goals：不推测原始失败原因；不更换邮箱凭据、不禁用 TLS 校验、不自动重发邮件。

## Decisions

- 遍历 cause、Jakarta Mail nextException 和 Spring MailSendException 的逐邮件异常，使用已知异常类型分类，不将原始服务商文案放入响应。
- 日志只允许固定错误键、Java 异常简单类名及 SMTP 数字状态码；采用对象身份集合避免异常链循环。
- 已实际复现 QQ 在 DATA 阶段返回 550 “The recipient may contain a non-existent account” 的拒绝，用户确认填错邮箱；该明确响应提示「邮箱不存在，请检查邮箱地址后重新发送」，其他 550 保留服务商拒绝分类。
- 只有 send 正常完成才返回成功；异常仍由上层销毁对应的 Redis 验证码挑战。

## Risks / Trade-offs

- SMTP 接受投递不等于收件箱已收到；一次成功测试也不证明先前失败已消失。
- 未识别的异常保留通用提示，但安全日志可以显示异常类型以供进一步排查。
