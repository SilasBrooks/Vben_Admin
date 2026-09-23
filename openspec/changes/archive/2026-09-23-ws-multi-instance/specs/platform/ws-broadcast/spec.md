# platform/ws-broadcast 变更

## ADDED Requirements

### Requirement: WebSocket 推送跨实例广播
站内通知与 IM 聊天的实时下行推送 SHALL 通过 Redis pub/sub 频道（`vben:ws:push`）广播：推送方 MUST 发布 `{kind, userId, payload}` 信封，每个后端实例 MUST 订阅该频道并仅向「本实例持有活跃会话」的接收人下发帧。单实例与多实例 MUST 走同一代码路径（发布者自身收到的广播即本地推送来源，禁止发布侧本地直推造成重复）。帧格式、WS 连接地址与保活协议 MUST 保持不变（前端零改动）。

#### Scenario: 跨实例实时可达
- **WHEN** 后端以多实例运行（共享同一 Redis），接收人的 WS 连接在实例 B，而触发推送的写操作由实例 A 处理
- **THEN** 实例 A 发布信封后实例 B 收到并经其本地注册表下发，接收人实时收到站内通知/IM 帧，无需刷新

#### Scenario: 单实例行为等价
- **WHEN** 后端以单实例运行且用户 WS 在线
- **THEN** 推送经 Redis 广播回到本实例并正常下发，行为与改造前一致，无重复帧

#### Scenario: 接收人不在线时静默兜底
- **WHEN** 接收人在任何实例均无活跃会话
- **THEN** 广播被各实例忽略（本地下发零目标），落库事实不受影响，用户上线后拉取可见

#### Scenario: Redis 不可用快速失败
- **WHEN** Redis 连接异常时发生推送
- **THEN** 发布侧异常仅记日志不抛出（落库主流程不受影响），不引入本地内存降级路径

### Requirement: 推送失败不影响业务主流程
推送链路的任何异常（发布侧序列化失败、订阅侧解析失败、单会话发送失败）MUST 仅记 warn 日志，MUST NOT 向上抛出、MUST NOT 中断订阅容器或影响同批其他会话。

#### Scenario: 单会话失败不扩散
- **WHEN** 同一用户的多个活跃会话中某个会话发送失败
- **THEN** 其余会话照常收到帧，异常仅记 warn
