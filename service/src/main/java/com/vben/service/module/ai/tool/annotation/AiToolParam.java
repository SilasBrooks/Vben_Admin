package com.vben.service.module.ai.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 描述 AI 工具方法的一个参数（写入生成给模型的 JSON Schema）。
 * 参数名由 Spring 的 ParameterNameDiscoverer 解析（-parameters 或调试符号表），无需手写。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiToolParam {

  /** 参数中文说明（模型据此理解如何填值） */
  String value();

  /** 是否必填；省略时选填（模型可不传，方法内收到 null） */
  boolean required() default false;
}
