package com.vben.service.module.notice.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.websocket.WsRedisBroadcaster;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 站内通知 WebSocket 处理器：维护用户 → 活跃会话的注册表并负责服务端推送。
 *
 * <p>鉴权在握手阶段完成（{@link NoticeHandshakeInterceptor} 解析 token 后把 userId
 * 放入 attributes），本处理器只负责连接生命周期管理与消息下发。
 * 同一用户可同时存在多个会话（多标签页），推送时逐一会话下发、互不影响；
 * 单会话推送失败仅告警，不中断其他会话也不向上抛出。
 *
 * <p>推送入口统一走 {@link WsRedisBroadcaster}（Redis pub/sub 跨实例广播）：
 * 业务方调用 broadcaster 发布信封，本实例订阅回调后经 {@link #sendToUser} 下发，
 * 多实例部署时跨实例帧不丢。启动时向 broadcaster 自注册本地推送器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeWebSocketHandler extends TextWebSocketHandler {

  /** userId -> 该用户的活跃会话集合（多标签页/多端登录场景） */
  private final Map<Long, Set<WebSocketSession>> registry = new ConcurrentHashMap<>();

  private final ObjectMapper objectMapper;
  private final WsRedisBroadcaster broadcaster;

  /** 启动时向广播器注册本地推送函数（订阅回调入口） */
  @PostConstruct
  void registerLocal() {
    broadcaster.registerLocalPusher(WsRedisBroadcaster.KIND_NOTICE, this::sendToUser);
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    Long userId = (Long) session.getAttributes().get("userId");
    if (userId == null) {
      // 握手拦截器保证必有 userId，兜底防御：取不到直接拒绝连接
      session.close(CloseStatus.NOT_ACCEPTABLE);
      return;
    }
    registry.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
    log.debug("WebSocket 连接建立 userId={} sessionId={}", userId, session.getId());
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    // 忽略客户端上行消息，仅回 pong 供前端保活探测
    try {
      session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
    } catch (Exception e) {
      log.warn("WebSocket pong 回复失败 sessionId={}", session.getId(), e);
    }
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    log.warn("WebSocket 传输异常 sessionId={}", session.getId(), exception);
    removeSession(session);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
    removeSession(session);
    log.debug("WebSocket 连接关闭 userId={} sessionId={} status={}",
        session.getAttributes().get("userId"), session.getId(), closeStatus);
  }

  /**
   * 向指定用户的全部活跃会话推送 JSON 消息。
   *
   * <p>会话非 open 时跳过；单个会话发送失败仅记 warn，不抛出——
   * 推送是通知的附加通道，落库才是事实来源，失败不应影响业务主流程。
   *
   * @param userId  接收人 id
   * @param payload 推送内容（Jackson 序列化为 JSON）
   */
  public void sendToUser(Long userId, Map<String, Object> payload) {
    Set<WebSocketSession> sessions = registry.get(userId);
    if (sessions == null || sessions.isEmpty()) {
      return;
    }
    String json;
    try {
      json = objectMapper.writeValueAsString(payload);
    } catch (Exception e) {
      log.warn("WebSocket 消息序列化失败 userId={}", userId, e);
      return;
    }
    for (WebSocketSession session : sessions) {
      if (!session.isOpen()) {
        continue;
      }
      try {
        // 同一会话可能被并发推送，串行化避免 TEXT_PARTIAL_WRITING 冲突
        synchronized (session) {
          session.sendMessage(new TextMessage(json));
        }
      } catch (Exception e) {
        log.warn("WebSocket 推送失败 userId={} sessionId={}", userId, session.getId(), e);
      }
    }
  }

  /** 连接关闭/异常时从注册表移除会话（并发下先判空再移除，集合空了顺带清 key） */
  private void removeSession(WebSocketSession session) {
    Long userId = (Long) session.getAttributes().get("userId");
    if (userId == null) {
      return;
    }
    Set<WebSocketSession> sessions = registry.get(userId);
    if (sessions != null) {
      sessions.remove(session);
      if (sessions.isEmpty()) {
        registry.remove(userId, sessions);
      }
    }
  }
}
