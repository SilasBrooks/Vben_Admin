# tasks

- [x] 1. `RedisKeys.wsPush()` 频道名常量
- [x] 2. `WsRedisBroadcaster`（发布信封 + 订阅分发 + kind 注册表）与 `WebSocketBroadcastConfig`（RedisMessageListenerContainer 装配）
- [x] 3. 两个 handler 自注册本地推送器；`NoticeService` / `ImChatService` 三处调用点改走 broadcaster
- [x] 4. 单测 `WsRedisBroadcasterTest`（发布序列化/分发到已注册推送器/未注册 kind 忽略/坏信封降级）
- [x] 5. `mvn -q compile` + `mvn test` 全量回归
- [x] 6. 双实例集成验证：本机 8080/8081 共享 Redis/PG，跨实例 IM 帧 + 站内通知实时可达
- [x] 7. README 更新（消除「WebSocket 单机边界」技术债条目 + 项目亮点）
- [ ] 8. 归档 openspec 变更（sync delta → archive）
