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
 * <p>请求维度为「name + 登录用户 id」，未登录场景降级为请求 IP。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {

  /** 业务动作标识（组成幂等键，需全局唯一） */
  String name();

  /** 幂等窗口（秒），窗口内重复请求被拒绝 */
  int intervalSeconds() default 10;

  /** 重复提交时的提示文案 */
  String message() default "请勿重复提交，请稍后再试";
}
