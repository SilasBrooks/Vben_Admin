# 设计：IM 消息聊天接入 AI 助手 + 公告增加发布人字段

## Context

- AI 工具自 `@AiAgentTool` 注解化改造后，新增工具零适配：在 `module/ai/tool/` 下建 `@Component` 类、方法加注解即可被 `AiToolRegistry` 反射扫描注册，权限码动态下发。
- IM 单聊全部 REST 接口为「登录即可」，无权限码；工具与 REST 权限语义保持一致（permission=""），越权面已有 ImChatService 按「本人数据」限定（发送校验对方启用、不能发给自己）。
- sys_notice 为「一条记录对应一个接收人」的扇出表，发布人信息适合以快照列冗余（发布时固化登录名），不建关联，避免 join 与用户改名后历史公告回溯困难。

## Goals / Non-Goals

- Goals：AI 助手可查询联系人/会话/历史并经确认后发送 IM 消息；公告落库与展示带发布人。
- Non-Goals：不新增 IM 群聊/引用发送等 AI 能力；不改变公告三粒度发布逻辑；不做发布人改名回溯同步。

## Decisions

- **D1 工具参数用 username 指代对象**：与既有 `publish_announcement`（usernames 列表）一致，后端经 `SysUserMapper` 解析 id；0 个或多个匹配即报错让模型追问，不猜测。联系人/会话/历史结果同时返回 username 与 nickname，模型可自行引用。
- **D2 发消息为普通 WRITE 工具**：不入 danger 名单——单发私信属低风险可逆操作（可删除），走标准确认卡片；复用 `ImChatService.send`（校验对方启用、禁发自发、限流在 controller 层不影响工具路径——工具调用频次由对话轮次天然限流）。
- **D3 历史消息限页**：`query_chat_history` 单次最多 50 条（与 REST pageSize 上限一致），默认 20 条，id 游标倒序后翻转为时间正序。
- **D4 publisher 存登录名快照**：`publisher VARCHAR(64) NULL`，公告广播时取 `LoginUserHolder.require().getUsername()`；安全类通知（系统自动触发）为 NULL，展示层以「—」占位。
- **D5 WS 推送载荷同步带 publisher**：公告实时帧加字段，铃铛浮层如需展示可直接取用（本期仅消息中心表格消费）。

## Risks / Trade-offs

- IM 工具无权限码 → 与 REST 一致，属产品语义而非漏洞；数据面由「本人 id」限定，无越权面。
- 存量库手工 ALTER 漏执行 → 后端查询报列不存在；沿用仓库既有约定（AGENTS.md 第 2 节），变更落地时输出 upgrade SQL 并直接应用到开发库。

## Migration Plan

1. 双库 schema 加列（幂等 IF NOT EXISTS / MySQL 判列存在性由文档给出）。
2. 存量库（vben5）直接执行 ALTER；历史公告 publisher 保持 NULL 显示「—」。
