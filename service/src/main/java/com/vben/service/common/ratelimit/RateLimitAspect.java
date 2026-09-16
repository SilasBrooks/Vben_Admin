package com.vben.service.common.ratelimit;

import com.vben.service.common.BizException;
import com.vben.service.common.IpUtil;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 固定窗口限流切面：key = 注解 name + 计数维度（IP 或登录用户 id）。
 *
 * <p>窗口过期由进入请求时惰性重置；窗口计数用 AtomicInteger 保证并发正确。
 * 表规模超阈值时惰性清理过期窗口，防止单条 key 无限增长。
 * 内存态属单实例语义，多实例部署需迁移 Redis（与验证码/锁定一致）。
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

  /** 触发惰性清理的表规模阈值 */
  private static final int MAX_WINDOWS = 10_000;

  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  @Around("@annotation(rateLimit)")
  public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
    long windowMs = rateLimit.windowSeconds() * 1000L;
    long now = System.currentTimeMillis();
    String key = rateLimit.name() + ":" + resolvePrincipal(rateLimit.scope());

    Window window = windows.compute(key,
        (k, old) -> old == null || now - old.start >= windowMs ? new Window(now) : old);
    if (windows.size() > MAX_WINDOWS) {
      windows.entrySet().removeIf(e -> now - e.getValue().start >= windowMs);
    }

    if (window.count.incrementAndGet() > rateLimit.limit()) {
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

  /** 单个固定窗口：start 为窗口起点毫秒值，count 为窗口内已进入的请求数 */
  private static final class Window {
    final long start;
    final AtomicInteger count = new AtomicInteger();

    Window(long start) {
      this.start = start;
    }
  }
}
