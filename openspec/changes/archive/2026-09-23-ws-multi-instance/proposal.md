# ws-multi-instance

## Why

站内通知与 IM 聊天的 WebSocket 推送注册表是各 handler 内的单机内存 `Map`（`NoticeWebSocketHandler` / `ImWebSocketHandler`）：服务重启丢全部活跃连接上下文（重连可恢复）；一旦横向扩容为多实例，接收人连在实例 B 上时，处理写操作的实例 A 无法触达其会话——通知与聊天帧静默丢失（落库仍在，实时性失效）。

## What Changes

- 新增 Redis pub/sub 广播组件 `WsRedisBroadcaster`：推送方统一「发布信封到 `vben:ws:push`」，每个实例订阅该频道并只向「本实例持有活跃会话」的用户下发；Redis pub/sub 天然广播含发布者自身，单实例与多实例行为统一
- `NoticeWebSocketHandler` / `ImWebSocketHandler` 启动时向 broadcaster 注册本地推送器，本地推送逻辑（多会话遍历、串行化、失败告警）原样保留
- `NoticeService.send` 与 `ImChatService` 两处推送调用点改为调用 broadcaster（推送由同步变异步，「失败不影响落库主流程」语义不变）
- RedisKeys 新增 pub/sub 频道名 `wsPush()`；新建 `RedisMessageListenerContainer` 装配（项目无既有 Redis 配置类）
- README 消除「WebSocket 单机边界」技术债条目，改为多实例语义说明

## Capabilities

- `platform/ws-broadcast`（ADDED，新能力目录）

## Impact

- 新增：`common/websocket/WsRedisBroadcaster`、`common/websocket/WebSocketBroadcastConfig`、单测
- 修改：`RedisKeys`、两个 WebSocket handler、`NoticeService`、`ImChatService`
- 不改前端（协议、帧格式、连接地址均不变）、不改表结构、不影响本地开发单实例链路
