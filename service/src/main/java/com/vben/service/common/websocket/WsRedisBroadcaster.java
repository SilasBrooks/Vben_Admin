package com.vben.service.common.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.redis.RedisKeys;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * WebSocket 推送跨实例广播器（Redis pub/sub）。
 *
 * <p>解决多实例部署时「写操作在实例 A、接收人连接在实例 B」导致的实时帧丢失：
 * 推送方统一调用 {@link #publish} 把 {kind, userId, payload} 信封发布到
 * {@link RedisKeys#wsPush()} 频道，每个实例订阅该频道并只向「本实例持有活跃会话」
 * 的接收人下发帧。Redis pub/sub 广播含发布者自身，因此发布侧不做本地直推——
 * 单实例与多实例走同一条「发布 → 订阅回调本地推送」路径，天然无重复帧。
 *
 * <p>依赖方向保持 common 不依赖 module：各 WebSocket handler 启动时调用
 * {@link #registerLocalPusher} 注册自己的本地推送函数（{@code handler::sendToUser}），
 * 广播器仅按 kind 分发。
 *
 * <p>失败语义：推送是通知/聊天的附加通道，落库才是事实来源——发布侧序列化失败、
 * 订阅侧解析失败均仅记 warn 不抛出；目标用户不在本实例时注册表未命中，自然忽略。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsRedisBroadcaster implements MessageListener {

  /** 消息类别：站内通知帧 */
  public static final String KIND_NOTICE = "notice";

  /** 消息类别：IM 聊天/已读回执帧 */
  public static final String KIND_IM = "im";

  /** kind -> 本实例本地推送函数（由各 WS handler 启动时自注册） */
  private final Map<String, BiConsumer<Long, Map<String, Object>>> localPushers =
      new ConcurrentHashMap<>();

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  /** 各 WebSocket handler 启动时注册本地推送器（sendToUser） */
  public void registerLocalPusher(String kind, BiConsumer<Long, Map<String, Object>> pusher) {
    localPushers.put(kind, pusher);
  }

  /**
   * 发布跨实例广播：信封 {kind, userId, payload} 发到 ws:push 频道。
   * 序列化/发布失败仅记 warn 不抛出（推送是附加通道，落库主流程不受影响）。
   */
  public void publish(String kind, Long userId, Map<String, Object> payload) {
    try {
      Map<String, Object> envelope = new LinkedHashMap<>();
      envelope.put("kind", kind);
      envelope.put("userId", userId);
      envelope.put("payload", payload);
      redis.convertAndSend(RedisKeys.wsPush(), objectMapper.writeValueAsString(envelope));
    } catch (Exception e) {
      log.warn("WebSocket 广播发布失败 kind={} userId={}", kind, userId, e);
    }
  }

  /**
   * 订阅回调：解析信封并分发给本实例的本地推送器。
   * 目标用户不在本实例时由 handler 注册表自然忽略；任何异常仅 warn，不中断订阅容器。
   */
  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      JsonNode root = objectMapper.readTree(
          new String(message.getBody(), StandardCharsets.UTF_8));
      String kind = root.path("kind").asText(null);
      JsonNode userIdNode = root.path("userId");
      JsonNode payloadNode = root.path("payload");
      if (kind == null || !userIdNode.canConvertToLong() || payloadNode.isMissingNode()) {
        log.warn("WebSocket 广播信封不完整，忽略");
        return;
      }
      BiConsumer<Long, Map<String, Object>> pusher = localPushers.get(kind);
      if (pusher == null) {
        // 本实例无该类别 handler（理论不发生），忽略
        return;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> payload = objectMapper.convertValue(payloadNode, Map.class);
      pusher.accept(userIdNode.asLong(), payload);
    } catch (Exception e) {
      log.warn("WebSocket 广播处理失败", e);
    }
  }
}
