package com.vben.service.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解（声明式，固定窗口计数，见 {@link RateLimitAspect}）。
 *
 * <p>状态保存在应用内存，属单实例语义；多实例部署需迁移至集中式存储（如 Redis）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

  /** 限流名称（作为计数 key 的前缀，同一接口全局唯一） */
  String name();

  /** 窗口内允许的最大请求次数 */
  int limit();

  /** 窗口长度（秒） */
  int windowSeconds();

  /** 计数维度：按客户端 IP 或按登录用户 */
  Scope scope() default Scope.IP;

  enum Scope {
    IP,
    USER
  }
}
