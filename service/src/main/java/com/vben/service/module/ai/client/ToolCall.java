package com.vben.service.module.ai.client;

/**
 * DeepSeek/OpenAI 协议的函数调用。
 *
 * @param id        工具调用 id（多轮回传时对应 tool_call_id）
 * @param type      固定 "function"
 * @param function  函数名与参数 JSON 字符串
 */
public record ToolCall(String id, String type, Function function) {

  public record Function(String name, String arguments) {
  }

  public static ToolCall of(String id, String name, String arguments) {
    return new ToolCall(id, "function", new Function(name, arguments));
  }
}
