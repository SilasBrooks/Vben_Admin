package com.vben.service.module.ai.agent;

import com.vben.service.module.ai.client.DeepMessage;
import java.util.List;

/**
 * 对话编排输出口：由 Web 层适配为 SSE 事件，编排逻辑不依赖 Servlet API。
 */
public interface AiChatSink {

  /** 正文流式片段 */
  void delta(String text);

  /**
   * 需要持久化进会话历史的消息（按顺序）：
   * 每轮模型回复（文本或 tool_calls）、查询工具的自动执行结果。
   * 前端收到后追加进 messages，供后续请求原样回传。
   */
  void history(List<DeepMessage> entries);

  /** 新增类工具：下发确认卡片，本轮结束 */
  void toolCall(PendingToolCall call);

  void done();

  void error(String message);
}
