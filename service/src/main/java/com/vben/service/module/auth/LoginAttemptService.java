package com.vben.service.module.auth;

import com.vben.service.common.BizException;
import com.vben.service.common.I18nMessage;
import com.vben.service.common.redis.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 登录失败锁定：按【用户名】与【客户端 IP】两个维度独立计数。
 *
 * <p>规则：15 分钟统计窗口内失败达 {@link #MAX_FAILURES} 次 → 锁定至窗口结束；
 * 锁定期间无论凭据正确与否一律拒绝（429 + 剩余等待时间）；登录成功清零对应计数。
 * 验证码失败不计入（防手误误锁），由接口限流兜底。
 * 计数存于 Redis（INCR + EXPIRE 原子脚本），窗口到期键自动消失，重启/多实例均有效。
 */
@Service
public class LoginAttemptService {

  /** 触发锁定的失败次数 */
  private static final int MAX_FAILURES = 5;
  /** 统计窗口 = 锁定时长（秒） */
  private static final long WINDOW_SECONDS = 15 * 60L;

  /** INCR 后首个失败设置窗口 TTL，保证计数必然过期（原子，避免无 TTL 残留键） */
  private static final DefaultRedisScript<Long> BUMP_SCRIPT = new DefaultRedisScript<>("""
      local c = redis.call('INCR', KEYS[1])
      if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
      return c
      """, Long.class);

  private final StringRedisTemplate redis;

  public LoginAttemptService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  /** 登录前检查：若任一维度已锁定则抛 429（含剩余等待分钟数） */
  public void checkLocked(String username, String ip) {
    checkOne(RedisKeys.loginFail("user", username.toLowerCase()), "error.login.lockTarget.user");
    checkOne(RedisKeys.loginFail("ip", ip), "error.login.lockTarget.ip");
  }

  /** 记录一次登录失败（凭据错误） */
  public void registerFailure(String username, String ip) {
    bump(RedisKeys.loginFail("user", username.toLowerCase()));
    bump(RedisKeys.loginFail("ip", ip));
  }

  /** 登录成功：清零两个维度的计数 */
  public void onSuccess(String username, String ip) {
    redis.delete(RedisKeys.loginFail("user", username.toLowerCase()));
    redis.delete(RedisKeys.loginFail("ip", ip));
  }

  private void checkOne(String key, String label) {
    String count = redis.opsForValue().get(key);
    if (count == null || Long.parseLong(count) < MAX_FAILURES) {
      return;
    }
    // 有计数必有 TTL（脚本保证），TTL<=0 视为窗口刚过期
    Long ttl = redis.getExpire(key);
    if (ttl == null || ttl <= 0) {
      return;
    }
    long remainMinutes = Math.max(1, (ttl + 59) / 60);
    throw BizException.tooManyRequests("error.login.locked",
        I18nMessage.get(label), remainMinutes);
  }

  private void bump(String key) {
    redis.execute(BUMP_SCRIPT, List.of(key), String.valueOf(WINDOW_SECONDS));
  }
}
