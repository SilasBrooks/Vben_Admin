package com.vben.service.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在 controller 写操作方法上，由 OperLogAspect 自动记录审计日志。
 *
 * <pre>{@code
 * @OperLog(module = "用户管理", description = "新增用户")
 * @PostMapping("/save")
 * public R<Void> save(...) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

  /** 所属模块，如「用户管理」 */
  String module();

  /** 操作描述，如「新增用户」 */
  String description() default "";
}
