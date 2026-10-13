package com.vben.service.module.ai.client;

import java.util.List;

/**
 * 流式响应回调：文本逐片段、工具调用在 finish_reason=tool_calls 时整体回调一次。
 */
public interface StreamHandler {

  /** 模型输出的正文片段 */
  void onText(String delta);

  /** 模型本轮发起的工具调用（arguments 已按分片聚合为完整 JSON 字符串） */
  void onToolCalls(List<ToolCall> toolCalls);

  /**
   * 单次上游请求的用量回调：每次 streamChat 流结束时恰回调一次
   * （上游返回 usage 分片时带 token 数，未返回时 tokens 为 null，调用次数仍可统计）。
   */
  default void onUsage(LlmUsage usage) {
  }
}
