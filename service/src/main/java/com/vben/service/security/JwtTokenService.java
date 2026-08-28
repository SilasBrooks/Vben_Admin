package com.vben.service.security;

import com.vben.service.config.VbenProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 双 Token 服务
 *
 * <p>access token：短生命周期，前端持有，走 Authorization: Bearer 头。
 * refresh token：长生命周期，httpOnly Cookie 持有，仅用于 /auth/refresh 换发。
 */
@Component
@RequiredArgsConstructor
public class JwtTokenService {

  public static final String CLAIM_ROLES = "roles";

  private final VbenProperties properties;

  private SecretKey accessKey() {
    return Keys.hmacShaKeyFor(
        properties.getJwt().getAccessTokenSecret().getBytes(StandardCharsets.UTF_8));
  }

  private SecretKey refreshKey() {
    return Keys.hmacShaKeyFor(
        properties.getJwt().getRefreshTokenSecret().getBytes(StandardCharsets.UTF_8));
  }

  public String generateAccessToken(Long userId, String username, List<String> roles) {
    return buildToken(userId, username, roles,
        properties.getJwt().getAccessTokenValidity().toMillis(), accessKey());
  }

  public String generateRefreshToken(Long userId, String username, List<String> roles) {
    return buildToken(userId, username, roles,
        properties.getJwt().getRefreshTokenValidity().toMillis(), refreshKey());
  }

  private String buildToken(Long userId, String username, List<String> roles,
      long ttlMillis, SecretKey key) {
    Date now = new Date();
    return Jwts.builder()
        .subject(username)
        .id(String.valueOf(userId))
        .claim(CLAIM_ROLES, roles)
        .issuedAt(now)
        .expiration(new Date(now.getTime() + ttlMillis))
        .signWith(key)
        .compact();
  }

  /**
   * 校验 access token
   *
   * @return 解析出的用户主体；无效/过期返回 null
   */
  public LoginUser parseAccessToken(String token) {
    return parse(token, accessKey());
  }

  /**
   * 校验 refresh token
   *
   * @return 解析出的用户主体；无效/过期返回 null
   */
  public LoginUser parseRefreshToken(String token) {
    return parse(token, refreshKey());
  }

  private LoginUser parse(String token, SecretKey key) {
    try {
      Claims claims = Jwts.parser().verifyWith(key).build()
          .parseSignedClaims(token).getPayload();
      Long userId = Long.valueOf(claims.getId());
      String username = claims.getSubject();
      @SuppressWarnings("unchecked")
      List<String> roles = claims.get(CLAIM_ROLES, List.class);
      return new LoginUser(userId, username, roles, null);
    } catch (JwtException | IllegalArgumentException e) {
      return null;
    }
  }
}
