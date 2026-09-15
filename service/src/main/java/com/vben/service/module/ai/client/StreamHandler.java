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
}
