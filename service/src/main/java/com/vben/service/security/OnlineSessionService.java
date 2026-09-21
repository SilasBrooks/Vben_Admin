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
 * <p>生命周期：登录成功写入（同账号重复登录覆盖 → 天然单会话）；TTL 为活动窗口
 * （{@code vben.online.activity-ttl}，默认 30 分钟），每次认证成功由 JwtAuthFilter 续期
 * ——窗口内无任何请求即视为离线，退出登录/强退/改密立即移除，避免"关浏览器"型残留
 * 长期占据在线列表。
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
          properties.getOnline().getActivityTtl());
    } catch (Exception e) {
      throw new IllegalStateException("在线会话写入失败", e);
    }
  }

  /** 登出/强制下线：移除会话 */
  public void remove(Long userId) {
    redis.delete(RedisKeys.online(userId));
  }

  /**
   * 认证成功：续期活动窗口；会话已过期但 token 仍有效时重建（昵称回退用户名）。
   * 展示型功能，Redis 异常不影响主请求。
   */
  public void touchOrRegister(Long userId, String username, String ip, long ver) {
    try {
      String key = RedisKeys.online(userId);
      if (redis.opsForValue().get(key) == null) {
        register(userId, username, username, ip, ver);
      } else {
        redis.expire(key, properties.getOnline().getActivityTtl());
      }
    } catch (Exception ignored) {
      // 在线展示为辅助功能，失败不影响正常请求
    }
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
        result.add(new OnlineUserView(userId, s.username(), s.nickname(), s.loginTime(), s.ip(),
            s.ver()));
      } catch (Exception ignored) {
        // 脏数据/正在过期的键，跳过
      }
    }
    return result;
  }

  /** Redis 中存储的会话体 */
  record Session(String username, String nickname, String loginTime, String ip, long ver) {
  }

  /** 对外列表视图（含从 key 反解出的 userId 与登录时 token 版本，供 ver 失配自愈） */
  public record OnlineUserView(Long userId, String username, String nickname,
      String loginTime, String ip, long ver) {
  }
}
