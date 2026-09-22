# 任务：IM 消息聊天接入 AI 助手 + 公告增加发布人字段

- [x] 1. 后端：新增 `AiImTools`（query_im_contacts / query_my_conversations / query_chat_history / send_chat_message）
- [x] 2. 后端：`SysNotice` 实体加 publisher 字段，`NoticeService` 公告广播落库记录发布人、WS 帧携带
- [x] 3. 数据库：`schema-postgres.sql` / `schema-mysql.sql` 加 publisher 列，输出存量库 upgrade SQL 并应用到开发库 vben5
- [x] 4. 前端：`api/notice.ts` NoticeItem 加 publisher；消息中心公告列表加发布人列；notice.json 中英文案
- [x] 5. 文档：README 工具数 21→25、IM 工具与公告发布人说明
- [x] 6. 验证：`mvn test` + `pnpm check:type` + 重启后端 E2E（登录→AI 对话发消息工具→确认卡片执行→消息中心看发布人）
- [x] 7. 归档 openspec 变更（sync delta → archive）
