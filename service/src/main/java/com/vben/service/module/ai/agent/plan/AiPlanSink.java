package com.vben.service.module.ai.agent.plan;

/**
 * 计划执行的 SSE 输出口（由 Web 层适配），编排逻辑不依赖 Servlet API。
 */
public interface AiPlanSink {

  /** 计划开始：下发 planId 与完整步骤（前端渲染执行流） */
  void started(String planId, String goal, java.util.List<PlanModels.StoredStep> steps);

  /** 某步开始执行 */
  void stepStart(int index, String title);

  /** 某步执行成功 */
  void stepDone(int index, String title, String summary);

  /** 遇到高危步骤：暂停等待用户二次确认（随后本次 SSE 结束） */
  void needConfirm(int index, String title, String reason);

  /** 某步失败，计划终止 */
  void failed(int index, String title, String error);

  /** 计划结束：status = completed / cancelled */
  void done(String status, java.util.List<PlanModels.StepResult> results);
}
