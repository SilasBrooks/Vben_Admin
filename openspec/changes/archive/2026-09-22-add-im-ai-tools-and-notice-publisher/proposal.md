# 变更提案：IM 消息聊天接入 AI 助手 + 公告增加发布人字段

## Why

1. IM 单聊模块已上线（联系人、会话、历史、发送），但 AI 助手无法触达：用户不能用自然语言让助手「给某人发条消息」「查一下我和某人的聊天记录」，AI 工具覆盖面缺口。
2. 公告广播（sys_notice）未记录发布人，接收人在消息中心只看到标题/正文/时间，无法知晓公告由谁发布，管理侧追溯性不足。

## What Changes

- 新增 4 个 IM 消息聊天 AI 工具（`@AiAgentTool` 注解声明，登录即可调用，与 IM REST 权限语义一致）：
  - `query_im_contacts` 查询可聊天联系人（QUERY）
  - `query_my_conversations` 查询我的会话列表（QUERY）
  - `query_chat_history` 查询与指定用户的聊天记录（QUERY）
  - `send_chat_message` 给指定用户发送消息（WRITE，走确认卡片）
- `sys_notice` 表新增 `publisher` 发布人登录名快照列：公告广播落库时记录当前登录用户，消息中心公告列表展示；安全类通知 publisher 为空。
- 内置 AI 工具总数 21 → 25。

## Capabilities

### 修改的能力

- `ai/assistant`：MODIFIED「工具集与两档执行语义」——移除固定的 8 个工具枚举（早已与 21 个工具的现实脱节），改为按类别描述与注解自动注册机制；新增「IM 消息聊天工具」需求。

### 新增的能力

- `system/notice`：站内通知/公告能力首次成文，含公告发布人记录与展示需求。

## Impact

- 后端：`service/src/main/java/com/vben/service/module/ai/tool/AiImTools.java`（新增）、`module/notice`（entity/service/DDL）
- 前端：`front/apps/web-ele/src/api/notice.ts`、`views/notice/center/index.vue`、`locales/langs/{zh-CN,en-US}/notice.json`
- 数据库：`schema-postgres.sql` / `schema-mysql.sql` 加列；存量库需手动执行 ALTER 语句（开发库 vben5 由本次变更直接应用）
- 文档：根 README.md 工具数与功能清单同步
