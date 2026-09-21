package com.vben.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用配置绑定（前缀 vben，见 application.yml）
 */
@Data
@ConfigurationProperties(prefix = "vben")
public class VbenProperties {

  private Jwt jwt = new Jwt();
  private Cookie cookie = new Cookie();
  private Cors cors = new Cors();
  private Online online = new Online();

  @Data
  public static class Jwt {
    private String accessTokenSecret;
    private String refreshTokenSecret;
    /** access token 有效期（默认 7 天，与前端 mock 对齐） */
    private Duration accessTokenValidity = Duration.ofDays(7);
    /** refresh token 有效期（默认 30 天） */
    private Duration refreshTokenValidity = Duration.ofDays(30);
  }

  @Data
  public static class Cookie {
    private String name = "jwt";
    private boolean httpOnly = true;
    /** Cookie 有效期（秒），与前端 mock 对齐 24h */
    private long maxAge = 86_400;
    /** dev(http) 用 Lax；生产 https 跨域用 None */
    private String sameSite = "Lax";
    private boolean secure = false;
  }

  @Data
  public static class Cors {
    private List<String> allowedOrigins = new ArrayList<>();
  }

  @Data
  public static class Online {
    /** 在线判定滑动窗口：每次认证成功续期，窗口内无任何请求即视为离线（列表自动消失） */
    private Duration activityTtl = Duration.ofMinutes(30);
  }
}
