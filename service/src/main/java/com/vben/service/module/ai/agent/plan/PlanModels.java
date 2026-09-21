package com.vben.service.module.ai.agent.plan;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * AI 多步计划（plan-and-execute）协议模型集合。
 */
public final class PlanModels {

  private PlanModels() {
  }

  /** 元工具名：模型用它提交执行计划，本身不落库、不分发到工具执行器 */
  public static final String SUBMIT_PLAN = "submit_plan";

  /** submit_plan 元工具的 DeepSeek/OpenAI schema（计划参数：目标 + 步骤数组） */
  public static Map<String, Object> submitPlanSchema() {
    Map<String, Object> stepProps = new java.util.LinkedHashMap<>();
    stepProps.put("tool", Map.of("type", "string",
        "description", "要调用的工具名，必须是下方工具清单中存在的名称"));
    stepProps.put("args", Map.of("type", "object",
        "description", "该工具的参数对象，字段与所选工具的 parameters 完全一致"));
    stepProps.put("reason", Map.of("type", "string",
        "description", "为什么需要这一步（给用户看的简短中文说明）"));

    Map<String, Object> properties = new java.util.LinkedHashMap<>();
    properties.put("goal", Map.of("type", "string",
        "description", "用户任务目标的一句话概述"));
    properties.put("steps", Map.of("type", "array",
        "description", "按执行顺序排列的步骤",
        "items", Map.of("type", "object", "properties", stepProps,
            "required", List.of("tool", "args"))));

    return Map.of("type", "function",
        "function", Map.of(
            "name", SUBMIT_PLAN,
            "description", "当一个任务需要两个及以上写操作、或步骤间有先后依赖时，"
                + "先提交完整执行计划交用户确认，不要直接逐个调用写工具。查询类步骤也可纳入计划。",
            "parameters", Map.of("type", "object", "properties", properties,
                "required", List.of("goal", "steps"))));
  }

  /**
   * 模型提交的单个步骤（submit_plan 参数）。
   *
   * @param tool   目标工具名
   * @param args   工具参数（模型产出的 JSON 对象）
   * @param reason 该步骤理由（计划卡展示给用户）
   */
  public record SubmittedStep(String tool, JsonNode args, String reason) {
  }

  /** 模型提交的计划 */
  public record SubmittedPlan(String goal, List<SubmittedStep> steps) {
  }

  /**
   * 校验后的持久化步骤：标题/高危标记/权限均以服务端注册中心实时数据为准，
   * 不信任前端回传值。
   */
  public record StoredStep(int index, String tool, String title, String argsJson,
                           String reason, boolean danger) {
  }

  /** 单步执行结果 */
  public record StepResult(int index, String title, boolean ok, String summary) {
  }

  /**
   * 高危步骤暂停态（JSON 存 Redis）：continue 时从 nextIndex 恢复。
   */
  public record PlanState(String planId, Long userId, String goal,
                          List<StoredStep> steps, int nextIndex, List<StepResult> results) {
  }

  /** 下发给前端的计划卡步骤视图 */
  public static Map<String, Object> stepView(StoredStep s) {
    return Map.of(
        "index", s.index(),
        "tool", s.tool(),
        "title", s.title(),
        "reason", s.reason() == null ? "" : s.reason(),
        "danger", s.danger(),
        "args", s.argsJson());
  }
}
