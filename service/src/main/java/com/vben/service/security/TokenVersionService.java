package com.vben.service.security;

import com.vben.service.common.redis.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 用户 token 版本号：集中管理"凭证变更即时失效"的版本计数。
 *
 * <p>版本号存于 Redis（无键视为 0）；签发 token 时把当时版本写入 JWT ver 声明，
 * 认证时比对当前版本，不一致即拒绝。以下动作必须 bump：改密、重置密码、禁用用户、强制下线。
 * bump 同时移除在线会话（被失效用户立即从在线列表消失）。
 */
@Service
public class TokenVersionService {

  private final StringRedisTemplate redis;
  private final OnlineSessionService onlineSessionService;

  public TokenVersionService(StringRedisTemplate redis, OnlineSessionService onlineSessionService) {
    this.redis = redis;
    this.onlineSessionService = onlineSessionService;
  }

  /** 当前版本号；无键 = 0（兼容历史 token 与新用户）。Redis 异常向上抛，由调用方 fail-closed */
  public long current(Long userId) {
    String v = redis.opsForValue().get(RedisKeys.tokenVer(userId));
    return v == null ? 0L : Long.parseLong(v);
  }

  /** 版本 +1 并移除在线会话：该用户已签发的 access/refresh token 立即全部失效 */
  public void bump(Long userId) {
    redis.opsForValue().increment(RedisKeys.tokenVer(userId));
    onlineSessionService.remove(userId);
  }
}
