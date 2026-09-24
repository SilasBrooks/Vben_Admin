## Tasks

- [x] 调整已绑定、未绑定及更换成功后的表单展示状态
- [x] 同步中英文按钮文案与 README
- [x] 完成前端类型检查及交互逻辑检查，通过 OpenSpec 严格校验
- [x] 归档 openspec 变更（sync delta → archive）

## Validation

- `front/` 下 `pnpm check:type` 通过。
- `openspec validate collapse-bound-recovery-email-form --strict` 通过。
- 代码检查确认：加载完成前不挂载表单；已绑定时点击更换才挂载表单；未绑定时直接挂载；成功后先重置表单再收起；邮件服务关闭时隐藏表单与更换按钮。
- `git diff --check` 未发现空白错误。本次未执行浏览器端实测。
