# design

## Context

- 两个 WS handler 结构一致：`Map<Long, Set<WebSocketSession>>` 本地注册表 + `sendToUser(userId, payload)`（多会话遍历、`synchronized(session)` 串行化、单会话失败仅 warn）。
- 推送调用点共 3 处：`NoticeService.send`（通知/公告）、`ImChatService`（chat 帧 + read 回执）。
- 项目 Redis 基建全部走 `StringRedisTemplate`（Spring Boot 自动装配），无自定义配置类、无 pub/sub 先例。
- 前端 WS 协议：服务端下行 JSON 帧（notice: `{type,id,title,content,publisher,createTime}`；im: `chat`/`read` 帧），上行仅 ping/pong——本次不改任何帧格式。

## Goals

- 多实例部署时跨实例推送可达（实例 A 处理写操作，接收人连在实例 B，实时帧不丢）
- 单实例行为与现状等价（同实例连接照常收到推送），重启不丢「已落库事实」
- 对前端零改动：连接地址、帧格式、保活机制全部不变

## Non-Goals

- 不做 WS 会话粘滞负载均衡/网关路由（应用层广播已解决可达性）
- 不合并两个重复的 handler 为抽象基类（避免超范围重构）
- 不引入 Spring Session WebSocket / STOMP（协议保持轻量自定义 JSON 帧）

## Decisions

- **D1 广播形态**：Redis pub/sub 单频道 `vben:ws:push`（`RedisKeys.wsPush()`）。信封 `{kind:"notice"|"im", userId, payload}`，`payload` 即原帧内容（结构不变）。发布者自身也会收到广播（pub/sub 语义），统一由订阅回调执行本地推送——发布侧不做本地直推，天然避免重复推送，单实例/多实例代码路径唯一。
- **D2 组件与依赖方向**：`common/websocket/WsRedisBroadcaster`（@Component，实现 `MessageListener`）+ `common/websocket/WebSocketBroadcastConfig`（装配 `RedisMessageListenerContainer` + adapter 绑定频道）。broadcaster 不依赖任何 module；handler 在 `@AfterPropertiesSet`（InitializingBean）向 broadcaster `registerLocalPusher(kind, handler::sendToUser)` 完成自注册，common → 单向依赖成立。
- **D3 kind 常量**：`WsRedisBroadcaster.KIND_NOTICE = "notice"`、`KIND_IM = "im"`，随组件集中管理。
- **D4 失败语义**：publish 侧仅可能序列化失败 → warn 不抛（调用方「落库为准」不变）；onMessage 侧解析/分发异常 → warn 不中断订阅容器。推送异步化后原「同步推送失败 warn」语义由订阅线程日志承接。
- **D5 序列化**：信封用 Spring Boot 全局 `ObjectMapper`（JavaTimeModule 已注册，payload 中 `LocalDateTime` 序列化为 ISO 字符串，与现状 `writeValueAsString(payload)` 行为一致）。反序列化 payload 到 `Map` 后二次序列化，数值型可能 int/long 归一——前端按 JSON 弱类型消费，无影响。
- **D6 装配细节**：`RedisMessageListenerContainer` 依赖 `RedisConnectionFactory`（自动装配）；`MessageListenerAdapter` 无委托（broadcaster 自身实现 onMessage），`addMessageListener(broadcaster, new ChannelTopic(RedisKeys.wsPush()))`。
- **D7 验证策略**：单测覆盖发布/分发/异常降级；集成验证起两个后端实例（8080/8081，共享同 Redis 与 PG），WS 客户端分别连两实例，从实例 A 触发 IM 发送/通知，断言连在实例 B 的客户端收到帧。

## Risks

- Redis 不可用时实时推送整体失效（含单实例）——与既有约定一致：分布式状态必须走 Redis，fail-fast，不降级；落库事实不受影响，用户拉取仍可见。
- pub/sub 无持久化：实例收发间隙离线的连接错过即错过（与现状「离线用户收不到实时帧」一致，落库兜底）。
- 广播风暴：IM 高频场景下每帧全实例广播——当前规模（演示/中小团队）无压力，未来量大可改 Redis Stream + 消费组，本期不引入。
