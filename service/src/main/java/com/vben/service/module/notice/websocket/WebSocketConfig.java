package com.vben.service.module.notice.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：注册站内通知端点。
 *
 * <p>服务 context-path 为 /api，浏览器实际连接地址为 /api/ws/notice?token=xxx。
 * 握手鉴权由 {@link NoticeHandshakeInterceptor} 自行完成（JWT 过滤器已放行 /ws/**），
 * 跨域放开来源（握手请求与 HTTP 侧 CORS 策略独立）。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

  private final NoticeWebSocketHandler noticeWebSocketHandler;
  private final NoticeHandshakeInterceptor noticeHandshakeInterceptor;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(noticeWebSocketHandler, "/ws/notice")
        .addInterceptors(noticeHandshakeInterceptor)
        .setAllowedOrigins("*");
  }
}
