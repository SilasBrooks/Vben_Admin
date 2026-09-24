## Tasks

- [x] 实现邮件错误分类和无敏感信息的日志
- [x] 同步中英文提示和 README 排查说明
- [x] 完成后端编译与测试，验证异常响应及日志不泄露秘密
- [x] 更新运行中的本地后端并验证启动健康
- [x] 通过 OpenSpec 严格校验
- [x] 归档 openspec 变更（sync delta → archive）

## Validation

- `mvn -q compile test` 通过；最终文案和 QQ 550 识别修改后，`mvn -q -Dtest=RecoveryMailSenderTest,EmailCodeServiceTest test` 通过，共 12 项邮件回归测试。
- 在测试中验证 cause、逐邮件异常和 QQ DATA 阶段的 550 分类；响应不携带底层异常，日志不包含邮箱、授权码、验证码、原始诊断文字或堆栈。
- 经用户授权使用现有配置向发件邮箱自身测试，SMTP 接受投递；对用户当时提供的目标邮箱测试，服务商明确返回 550 收件账号可能不存在，用户随后确认地址填写错误。
- 已重启本项目 18080 开发后端，`GET /api/auth/recovery/options` 返回成功且 `enabled=true`。没有向用户后来更正的地址额外发送测试邮件。
- `openspec validate diagnose-smtp-delivery-failures --strict` 通过。
