package com.vben.service.common.ratelimit;

import com.vben.service.common.BizException;
import com.vben.service.common.IpUtil;
import com.vben.service.common.redis.RedisKeys;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 固定窗口限流切面：key = 注解 name + 计数维度（IP 或登录用户 id）。
 *
 * <p>计数窗口存于 Redis（INCR + EXPIRE 原子脚本，窗口 = TTL）：
 * 重启后进行中的窗口保持有效，多实例共享同一计数。
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

  /** INCR 后首个请求设置窗口 TTL，保证窗口必然过期（原子，避免无 TTL 残留键） */
  private static final DefaultRedisScript<Long> BUMP_SCRIPT = new DefaultRedisScript<>("""
      local c = redis.call('INCR', KEYS[1])
      if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
      return c
      """, Long.class);

  private final StringRedisTemplate redis;

  public RateLimitAspect(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Around("@annotation(rateLimit)")
  public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
    String key = RedisKeys.rateLimit(rateLimit.name(), resolvePrincipal(rateLimit.scope()));
    Long count = redis.execute(BUMP_SCRIPT, List.of(key),
        String.valueOf(rateLimit.windowSeconds()));

    if (count != null && count > rateLimit.limit()) {
      log.warn("限流触发: key={}, limit={}/{}s", key, rateLimit.limit(), rateLimit.windowSeconds());
      throw BizException.tooManyRequests("请求过于频繁，请稍后再试");
    }
    return pjp.proceed();
  }

  private String resolvePrincipal(RateLimit.Scope scope) {
    if (scope == RateLimit.Scope.USER) {
      LoginUser user = LoginUserHolder.get();
      if (user != null) {
        return "u" + user.getUserId();
      }
      // 未登录场景（如 token 未带）降级按 IP 计数，避免 NPE
    }
    ServletRequestAttributes attrs =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      return "unknown";
    }
    return IpUtil.getClientIp(attrs.getRequest());
  }
}
