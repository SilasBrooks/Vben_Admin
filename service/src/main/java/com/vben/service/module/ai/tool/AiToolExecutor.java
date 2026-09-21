package com.vben.service.module.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.BizException;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI 工具执行器：参数解析 → 登录用户权限校验 → 经注册中心反射调用工具方法。
 *
 * <p>工具方法与分发不再硬编码：新增工具只需在 Spring Bean 方法上加
 * {@link com.vben.service.module.ai.tool.annotation.AiAgentTool}，启动时自动注册到
 * {@link AiToolRegistry}。
 *
 * <p>两种调用姿态：
 * <ul>
 *   <li>{@link #runStrict}：用户确认后的写操作（含计划执行），业务错误直接抛异常</li>
 *   <li>{@link #runQuiet}：编排循环内自动执行查询，业务错误转为失败结果回喂模型</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AiToolExecutor {

  private final AiToolRegistry registry;
  private final ObjectMapper objectMapper;

  /** 确认执行（写操作/计划步骤）：权限/参数错误抛 BizException，由全局异常处理返回前端 */
  public AiToolResult runStrict(String toolName, String argsJson) {
    ToolHandler handler = registry.require(toolName);
    return invoke(handler, argsJson);
  }

  /** 自动执行（查询工具）：业务错误转为失败结果回喂模型，不中断整轮对话 */
  public AiToolResult runQuiet(String toolName, String argsJson) {
    try {
      ToolHandler handler = registry.require(toolName);
      return invoke(handler, argsJson);
    } catch (BizException e) {
      return AiToolResult.fail(e.getMessage());
    }
  }

  private AiToolResult invoke(ToolHandler handler, String argsJson) {
    AiToolDef def = handler.def();
    LoginUser loginUser = LoginUserHolder.get();
    if (loginUser == null) {
      throw BizException.unauthorized("error.unauthorized");
    }
    if (!registry.canUse(loginUser, def)) {
      throw BizException.forbidden("error.ai.noPermission", def.title());
    }
    JsonNode args = parseArgs(argsJson);
    Object returned = handler.invoke(args);
    if (returned instanceof AiToolResult result) {
      return result;
    }
    // 工具方法也可直接返回数据对象/字符串：统一序列化包装
    if (returned instanceof String text) {
      return AiToolResult.success(text);
    }
    try {
      return AiToolResult.success(objectMapper.writeValueAsString(returned));
    } catch (Exception e) {
      throw new IllegalStateException("工具结果序列化失败：" + def.name(), e);
    }
  }

  private JsonNode parseArgs(String argsJson) {
    try {
      if (argsJson == null || argsJson.isBlank()) {
        return objectMapper.createObjectNode();
      }
      return objectMapper.readTree(argsJson);
    } catch (Exception e) {
      throw BizException.badRequest("error.ai.badJson");
    }
  }
}
