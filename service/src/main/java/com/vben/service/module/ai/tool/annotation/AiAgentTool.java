package com.vben.service.module.ai.tool.annotation;

import com.vben.service.module.ai.tool.AiToolKind;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注一个 Spring Bean 的 public 方法为 AI 助手可调用的工具。
 *
 * <p>启动时由 {@code AiToolRegistry} 反射扫描自动注册：工具元数据（name/description/参数
 * JSON Schema）全部来自注解与方法签名，新增工具只需在业务方法上加本注解，无需修改任何
 * 注册表或分发代码。反射调用走 Spring 代理 Bean，方法内 {@code @DataScope/@Idempotent/
 * @OperLog} 等切面照常生效。
 *
 * <p>方法约定：返回 {@code AiToolResult}；参数使用 {@link AiToolParam} 提供中文说明。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiAgentTool {

  /** 工具名（模型 function.name），全局唯一，小写下划线风格，如 query_users */
  String name();

  /** 中文动作名（确认卡片/计划步骤标题，如「创建部门」） */
  String title();

  /** 查询（自动执行）或写入（需确认） */
  AiToolKind kind();

  /** 执行所需功能权限码；为空字符串表示仅登录即可调用 */
  String permission() default "";

  /** 给模型看的中文说明：工具做什么、什么场景调用、参数语义、注意事项 */
  String description();

  /**
   * 高危操作标记：删除、改密、授权变更、停用、强制下线等。
   * 普通确认卡片仍需点击；在多步计划中执行到该步骤会暂停并二次确认。
   */
  boolean danger() default false;
}
