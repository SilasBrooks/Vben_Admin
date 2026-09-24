package com.vben.service.common.idempotent;

import com.vben.service.common.BizException;
import com.vben.service.common.I18nMessage;
import com.vben.service.common.IpUtil;
import com.vben.service.common.redis.RedisKeys;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 防重复提交切面：key = vben:idempotent:{name}:{principal}（principal = 登录用户 id，未登录降级 IP）。
 *
 * <p>占用窗口存于 Redis（SET NX EX 原子脚本，TTL = 幂等窗口）：
 * 首次请求占位成功后放行，窗口内的后续请求直接拒绝（409），重启/多实例语义一致。
 * 可选业务键继续隔离；执行期间防重模式在 finally 中仅释放自身令牌对应的锁。
 */
@Slf4j
@Aspect
@Component
public class IdempotentAspect {

  /** 首次请求占位（NX 保证只成功一次），TTL = 窗口时长，保证窗口必然过期（原子，避免无 TTL 残留键） */
  private static final DefaultRedisScript<Long> CLAIM_SCRIPT = new DefaultRedisScript<>("""
      local ok = redis.call('SET', KEYS[1], ARGV[2], 'NX', 'EX', ARGV[1])
      if ok then return 1 end
      return 0
      """, Long.class);

  private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
      if redis.call('GET', KEYS[1]) == ARGV[1] then
        return redis.call('DEL', KEYS[1])
      end
      return 0
      """, Long.class);

  private final StringRedisTemplate redis;

  public IdempotentAspect(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Around("@annotation(idempotent)")
  public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
    String key = RedisKeys.idempotent(idempotent.name(), resolvePrincipal());
    if (!idempotent.key().isBlank()) {
      var context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
      Object[] args = pjp.getArgs();
      for (int i = 0; i < args.length; i++) context.setVariable("p" + i, args[i]);
      Object businessKey = new SpelExpressionParser().parseExpression(idempotent.key()).getValue(context);
      key += ":" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(String.valueOf(businessKey).getBytes(StandardCharsets.UTF_8)));
    }
    String lockToken = UUID.randomUUID().toString();
    Long claimed = redis.execute(CLAIM_SCRIPT, List.of(key),
        String.valueOf(idempotent.intervalSeconds()), lockToken);

    if (claimed == null || claimed != 1L) {
      log.warn("重复提交被拒: key={}, window={}s", key, idempotent.intervalSeconds());
      throw new BizException(409, I18nMessage.get(idempotent.message()));
    }
    try {
      return pjp.proceed();
    } finally {
      if (idempotent.releaseAfterCompletion()) {
        redis.execute(RELEASE_SCRIPT, List.of(key), lockToken);
      }
    }
  }

  private String resolvePrincipal() {
    LoginUser user = LoginUserHolder.get();
    if (user != null) {
      return "u" + user.getUserId();
    }
    ServletRequestAttributes attrs =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      return "unknown";
    }
    return IpUtil.getClientIp(attrs.getRequest());
  }
}
