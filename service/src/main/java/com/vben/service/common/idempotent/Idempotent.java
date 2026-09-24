package com.vben.service.common.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 防重复提交：同一请求者在 intervalSeconds 窗口内只能成功提交一次，
 * 窗口内重复请求返回 409。计数存 Redis（SETNX+EXPIRE 原子），多实例共享。
 *
 * <p>默认请求维度为「name + 登录用户 id」，未登录场景降级为请求 IP；
 * 可通过 key 追加业务维度，或通过 releaseAfterCompletion 使用执行期间防重。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {

  /** 业务动作标识（组成幂等键，需全局唯一） */
  String name();

  /** 可选业务键 SpEL（#p0 为第一个参数），在请求者维度下继续隔离。 */
  String key() default "";

  /** 为 true 时仅阻止执行期间的并发提交，完成后释放自身锁；默认保留时间窗防重。 */
  boolean releaseAfterCompletion() default false;

  /** 幂等窗口（秒），窗口内重复请求被拒绝 */
  int intervalSeconds() default 10;

  /** 重复提交提示的消息 key（messages.properties，切面按请求语言解析） */
  String message() default "error.idempotent.duplicate";
}
