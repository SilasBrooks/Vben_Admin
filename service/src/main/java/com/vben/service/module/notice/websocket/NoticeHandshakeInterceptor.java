package com.vben.service.module.notice.websocket;

import com.vben.service.security.JwtTokenService;
import com.vben.service.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手鉴权拦截器：从握手 URL 的 query 参数 token 中解析 access token。
 *
 * <p>浏览器 WebSocket API 无法自定义请求头，故与前端约定通过
 * /api/ws/notice?token=xxx 携带凭据。解析走 {@link JwtTokenService#parseAccessToken}，
 * 缺参/验签失败/过期直接置 401 并拒绝握手（返回 false，避免落入异常处理产生 500 噪音日志），
 * 未鉴权连接不会进入 {@link NoticeWebSocketHandler}。
 * 解析成功后把 userId 写入 attributes，供后续注册表与推送使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeHandshakeInterceptor implements HandshakeInterceptor {

  private final JwtTokenService jwtTokenService;

  @Override
  public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler wsHandler, Map<String, Object> attributes) {
    String token = UriComponentsBuilder.fromUri(request.getURI())
        .build().getQueryParams().getFirst("token");
    if (token == null || token.isBlank()) {
      return reject(response, "缺少 token 参数");
    }
    // 与 HTTP 侧一致：仅校验签名与有效期，无效/过期统一返回 null
    LoginUser user = jwtTokenService.parseAccessToken(token);
    if (user == null) {
      return reject(response, "token 无效或已过期");
    }
    attributes.put("userId", user.getUserId());
    return true;
  }

  /** 拒绝握手：置 401、记 warn 日志并返回 false */
  private boolean reject(ServerHttpResponse response, String reason) {
    log.warn("WebSocket 握手被拒绝：{} ", reason);
    response.setStatusCode(HttpStatus.UNAUTHORIZED);
    return false;
  }

  @Override
  public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
      WebSocketHandler wsHandler, Exception exception) {
    // 无需处理
  }
}
