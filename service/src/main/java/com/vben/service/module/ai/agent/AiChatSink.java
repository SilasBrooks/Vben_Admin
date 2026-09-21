package com.vben.service.module.ai.agent;

import com.vben.service.module.ai.agent.plan.PlanModels;
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
   * 每轮模型回复（文本或 tool_calls）、查询工具的自动执行结果、计划校验失败的回喂。
   * 前端收到后追加进 messages，供后续请求原样回传。
   */
  void history(List<DeepMessage> entries);

  /** 单个写操作：下发确认卡片，本轮结束 */
  void toolCall(PendingToolCall call);

  /**
   * 多步写任务：下发执行计划卡片，本轮结束。
   * 前端渲染目标与步骤清单，用户确认后调 /ai/plan/execute。
   */
  void plan(String toolCallId, String goal, List<PlanModels.StoredStep> steps);

  void done();

  void error(String message);
}
