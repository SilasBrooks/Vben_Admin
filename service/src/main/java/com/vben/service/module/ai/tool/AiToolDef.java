package com.vben.service.module.ai.tool;

import java.util.Map;

/**
 * AI 工具定义（与 DeepSeek tools 协议一一对应）。
 *
 * @param name        工具名（模型 function.name）
 * @param title       中文动作名（确认卡片标题，如"创建部门"）
 * @param kind        查询 / 新增
 * @param permission  执行所需功能权限码
 * @param description 给模型看的中文说明
 * @param parameters  OpenAI JSON Schema 参数定义
 */
public record AiToolDef(String name, String title, AiToolKind kind, String permission,
                        String description, Map<String, Object> parameters) {

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
