package com.vben.service.module.ai.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * OpenAI 协议形态的对话消息（DeepSeek 兼容）。
 *
 * <ul>
 *   <li>普通消息：system / user / assistant(文本)</li>
 *   <li>助手发起工具调用：assistant + toolCalls（content 为 null）</li>
 *   <li>工具执行结果：role=tool + toolCallId</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeepMessage {

  private String role;
  private String content;
  @JsonProperty("tool_calls")
  private List<ToolCall> toolCalls;
  @JsonProperty("tool_call_id")
  private String toolCallId;

  public DeepMessage() {
  }

  private DeepMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId) {
    this.role = role;
    this.content = content;
    this.toolCalls = toolCalls;
    this.toolCallId = toolCallId;
  }

  public static DeepMessage system(String content) {
    return new DeepMessage("system", content, null, null);
  }

  public static DeepMessage user(String content) {
    return new DeepMessage("user", content, null, null);
  }

  public static DeepMessage assistant(String content) {
    return new DeepMessage("assistant", content, null, null);
  }

  /** 助手发起工具调用（content 为已输出的引导语，可为 null） */
  public static DeepMessage assistantToolCalls(String content, List<ToolCall> toolCalls) {
    return new DeepMessage("assistant", content, toolCalls, null);
  }

  /** 工具执行结果回喂 */
  public static DeepMessage toolResult(String toolCallId, String content) {
    return new DeepMessage("tool", content, null, toolCallId);
  }

  public String getRole() {
    return role;
  }

  public String getContent() {
    return content;
  }

  public List<ToolCall> getToolCalls() {
    return toolCalls;
  }

  public String getToolCallId() {
    return toolCallId;
  }
}
