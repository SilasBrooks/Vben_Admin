package com.vben.service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.redis.RedisKeys;
import com.vben.service.config.VbenProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 在线会话登记：Redis 记录当前在线用户（键 = 用户 id，值 = 会话信息 JSON）。
 *
 * <p>生命周期：登录成功写入（同账号重复登录覆盖 → 天然单会话），
 * TTL 与 access token 有效期一致；登出/版本号 bump 时移除。
 */
@Service
public class OnlineSessionService {

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final VbenProperties properties;

  public OnlineSessionService(StringRedisTemplate redis, ObjectMapper objectMapper,
      VbenProperties properties) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  /** 登录成功：登记在线会话（覆盖旧会话） */
  public void register(Long userId, String username, String nickname, String ip, long ver) {
    Session session = new Session(username, nickname,
        LocalDateTime.now().withNano(0).toString(), ip, ver);
    try {
      redis.opsForValue().set(RedisKeys.online(userId), objectMapper.writeValueAsString(session),
          properties.getJwt().getAccessTokenValidity());
    } catch (Exception e) {
      throw new IllegalStateException("在线会话写入失败", e);
    }
  }

  /** 登出/强制下线：移除会话 */
  public void remove(Long userId) {
    redis.delete(RedisKeys.online(userId));
  }

  /**
   * 全量在线会话列表。
   *
   * <p>当前用 KEYS 匹配（模板项目规模足够）；在线用户量大时必须换 SCAN 游标遍历。
   */
  public List<OnlineUserView> listAll() {
    Set<String> keys = redis.keys(RedisKeys.onlinePattern());
    List<OnlineUserView> result = new ArrayList<>();
    if (keys == null) {
      return result;
    }
    for (String key : keys) {
      String json = redis.opsForValue().get(key);
      if (json == null) {
        continue;
      }
      try {
        Session s = objectMapper.readValue(json, Session.class);
        Long userId = Long.valueOf(key.substring(RedisKeys.PREFIX.length() + "online:".length()));
        result.add(new OnlineUserView(userId, s.username(), s.nickname(), s.loginTime(), s.ip()));
      } catch (Exception ignored) {
        // 脏数据/正在过期的键，跳过
      }
    }
    return result;
  }

  /** Redis 中存储的会话体 */
  record Session(String username, String nickname, String loginTime, String ip, long ver) {
  }

  /** 对外列表视图（含从 key 反解出的 userId） */
  public record OnlineUserView(Long userId, String username, String nickname,
      String loginTime, String ip) {
  }
}
