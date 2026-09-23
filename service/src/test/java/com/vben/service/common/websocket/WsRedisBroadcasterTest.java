package com.vben.service.common.websocket;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.redis.RedisKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * WsRedisBroadcaster 单测（纯 Mockito，无 Redis/容器依赖）。
 *
 * <p>覆盖：发布信封结构（kind/userId/payload）与频道名、发布序列化失败不抛出、
 * 订阅分发到已注册推送器、未注册 kind 忽略、坏信封降级不中断。
 */
class WsRedisBroadcasterTest {

  private StringRedisTemplate redis;
  private ObjectMapper objectMapper;
  private WsRedisBroadcaster broadcaster;

  @BeforeEach
  void setUp() {
    redis = mock(StringRedisTemplate.class);
    objectMapper = new ObjectMapper();
    broadcaster = new WsRedisBroadcaster(redis, objectMapper);
  }

  /** 构造一条订阅回调消息（模拟 Redis 投递到 ws:push 频道的信封） */
  private static Message envelope(String kind, long userId, Map<String, Object> payload)
      throws Exception {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("kind", kind);
    m.put("userId", userId);
    m.put("payload", payload);
    String json = new ObjectMapper().writeValueAsString(m);
    return new DefaultMessage(RedisKeys.wsPush().getBytes(), json.getBytes());
  }

  @Test
  void publish_sendsEnvelopeToWsPushChannel() throws Exception {
    Map<String, Object> payload = Map.of("type", "chat");

    broadcaster.publish(WsRedisBroadcaster.KIND_IM, 7L, payload);

    ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
    verify(redis).convertAndSend(eq(RedisKeys.wsPush()), json.capture());
    var root = new ObjectMapper().readTree(json.getValue());
    org.junit.jupiter.api.Assertions.assertEquals("im", root.path("kind").asText());
    org.junit.jupiter.api.Assertions.assertEquals(7, root.path("userId").asLong());
    org.junit.jupiter.api.Assertions.assertEquals("chat", root.path("payload").path("type").asText());
  }

  @Test
  void publish_serializationFailure_swallowed() throws Exception {
    ObjectMapper brokenMapper = mock(ObjectMapper.class);
    when(brokenMapper.writeValueAsString(any())).thenThrow(new RuntimeException("bad"));
    WsRedisBroadcaster broken = new WsRedisBroadcaster(redis, brokenMapper);

    assertDoesNotThrow(() ->
        broken.publish(WsRedisBroadcaster.KIND_NOTICE, 1L, Map.of("k", "v")));
  }

  @Test
  void publish_redisFailure_swallowed() {
    doThrow(new RuntimeException("redis down")).when(redis)
        .convertAndSend(anyString(), anyString());

    assertDoesNotThrow(() ->
        broadcaster.publish(WsRedisBroadcaster.KIND_NOTICE, 1L, Map.of()));
  }

  @Test
  void onMessage_dispatchesToRegisteredPusher() throws Exception {
    @SuppressWarnings("unchecked")
    BiConsumer<Long, Map<String, Object>> pusher = mock(BiConsumer.class);
    broadcaster.registerLocalPusher(WsRedisBroadcaster.KIND_IM, pusher);

    broadcaster.onMessage(envelope(WsRedisBroadcaster.KIND_IM, 7L, Map.of("type", "chat")), null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
    verify(pusher).accept(eq(7L), payload.capture());
    org.junit.jupiter.api.Assertions.assertEquals("chat", payload.getValue().get("type"));
  }

  @Test
  void onMessage_unregisteredKind_ignored() throws Exception {
    @SuppressWarnings("unchecked")
    BiConsumer<Long, Map<String, Object>> pusher = mock(BiConsumer.class);
    broadcaster.registerLocalPusher(WsRedisBroadcaster.KIND_IM, pusher);

    broadcaster.onMessage(envelope(WsRedisBroadcaster.KIND_NOTICE, 7L, Map.of()), null);

    verify(pusher, never()).accept(any(), any());
  }

  @Test
  void onMessage_malformedEnvelope_swallowed() {
    Message bad = new DefaultMessage(RedisKeys.wsPush().getBytes(), "{not-json".getBytes());

    assertDoesNotThrow(() -> broadcaster.onMessage(bad, null));
  }

  @Test
  void onMessage_incompleteEnvelope_ignored() {
    Message missing = new DefaultMessage(
        RedisKeys.wsPush().getBytes(), "{\"kind\":\"im\"}".getBytes());

    @SuppressWarnings("unchecked")
    BiConsumer<Long, Map<String, Object>> pusher = mock(BiConsumer.class);
    broadcaster.registerLocalPusher(WsRedisBroadcaster.KIND_IM, pusher);

    assertDoesNotThrow(() -> broadcaster.onMessage(missing, null));
    verify(pusher, never()).accept(any(), any());
  }
}
