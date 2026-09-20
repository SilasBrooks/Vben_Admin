package com.vben.service.module.im.websocket;

import com.vben.service.module.notice.websocket.NoticeHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * IM 聊天 WebSocket 配置：注册 /ws/im 端点。
 *
 * <p>服务 context-path 为 /api，浏览器实际连接地址为 /api/ws/im?token=xxx。
 * 与通知端点 /ws/notice 相互独立（各自注册表、各自生命周期），
 * 握手鉴权逻辑通用，直接复用 notice 模块的 {@link NoticeHandshakeInterceptor}。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ImWebSocketConfig implements WebSocketConfigurer {

  private final ImWebSocketHandler imWebSocketHandler;
  private final NoticeHandshakeInterceptor noticeHandshakeInterceptor;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(imWebSocketHandler, "/ws/im")
        .addInterceptors(noticeHandshakeInterceptor)
        .setAllowedOrigins("*");
  }
}
