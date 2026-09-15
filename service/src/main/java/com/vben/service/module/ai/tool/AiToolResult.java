package com.vben.service.module.ai.tool;

/**
 * 工具执行结果：content 直接作为 role=tool 消息回喂模型。
 *
 * @param ok      是否成功
 * @param content 成功为精简 JSON；失败为中文错误说明（模型据此追问/解释）
 * @param summary 写操作成功后的人类可读摘要（确认卡片展示）
 */
public record AiToolResult(boolean ok, String content, String summary) {

  public static AiToolResult success(String contentJson) {
    return new AiToolResult(true, contentJson, null);
  }

  public static AiToolResult created(String contentJson, String summary) {
    return new AiToolResult(true, contentJson, summary);
  }

  public static AiToolResult fail(String message) {
    return new AiToolResult(false, message, null);
  }
}
