package com.vben.service.module.auth;

import com.vben.service.config.VbenProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * refreshToken Cookie 读写（httpOnly，名称与前端约定为 jwt）
 *
 * <p>dev(http) 与 prod(https) 的 SameSite/Secure 策略通过配置切换，
 * 见 application.yml vben.cookie。
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenCookieService {

  private final VbenProperties properties;

  public void write(HttpServletResponse response, String refreshToken) {
    VbenProperties.Cookie conf = properties.getCookie();
    ResponseCookie cookie = ResponseCookie.from(conf.getName(), refreshToken)
        .path("/")
        .httpOnly(conf.isHttpOnly())
        .maxAge(conf.getMaxAge())
        .sameSite(conf.getSameSite())
        .secure(conf.isSecure())
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  public void clear(HttpServletResponse response) {
    VbenProperties.Cookie conf = properties.getCookie();
    ResponseCookie cookie = ResponseCookie.from(conf.getName(), "")
        .path("/")
        .httpOnly(conf.isHttpOnly())
        .maxAge(0)
        .sameSite(conf.getSameSite())
        .secure(conf.isSecure())
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  public String read(HttpServletRequest request) {
    jakarta.servlet.http.Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    String name = properties.getCookie().getName();
    for (jakarta.servlet.http.Cookie cookie : cookies) {
      if (name.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }
}
