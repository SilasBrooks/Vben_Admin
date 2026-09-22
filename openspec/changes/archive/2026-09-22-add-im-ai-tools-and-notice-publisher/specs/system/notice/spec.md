# delta: system/notice

## ADDED Requirements

### Requirement: 公告发布人记录与展示

公告广播落库时 MUST 在 `sys_notice.publisher` 列记录发布人登录名快照（发布时刻的当前登录用户，不随其后改名回溯更新）；系统自动触发的安全类通知 publisher MUST 为 NULL。通知列表接口（`GET /api/notice/list`）返回数据 MUST 包含 publisher 字段；消息中心公告列表 MUST 展示发布人列，安全类通知该列以「—」占位。WebSocket 公告实时帧 MUST 同步携带 publisher。

#### Scenario: 公告落库记录发布人

- **WHEN** 管理员在公告发布页（或经 AI 助手）发布一条公告
- **THEN** 每个接收人的 sys_notice 记录 publisher 为该管理员的登录名

#### Scenario: 消息中心展示发布人

- **WHEN** 用户在消息中心查看公告列表
- **THEN** 公告行显示发布人登录名，历史存量公告（publisher 为空）显示「—」

#### Scenario: 安全通知不显示发布人

- **WHEN** 用户在消息中心查看安全类通知
- **THEN** 发布人列显示「—」
