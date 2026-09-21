package com.vben.service.module.ai.tool;

import java.util.Map;

/**
 * AI 工具定义（与 DeepSeek tools 协议一一对应），由 {@code ToolHandler} 从注解与方法签名生成。
 *
 * @param name        工具名（模型 function.name）
 * @param title       中文动作名（确认卡片/计划步骤标题，如"创建部门"）
 * @param kind        查询 / 写入
 * @param permission  执行所需功能权限码（空串=仅登录即可）
 * @param danger      是否高危（计划执行到该步骤需二次确认）
 * @param description 给模型看的中文说明
 * @param parameters  OpenAI JSON Schema 参数定义
 */
public record AiToolDef(String name, String title, AiToolKind kind, String permission,
                        boolean danger, String description, Map<String, Object> parameters) {

  /** 转为 DeepSeek/OpenAI tools 数组元素 */
  public Map<String, Object> toSchema() {
    return Map.of(
        "type", "function",
        "function", Map.of(
            "name", name,
            "description", description,
            "parameters", parameters));
  }
}
