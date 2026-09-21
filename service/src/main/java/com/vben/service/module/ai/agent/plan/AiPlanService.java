package com.vben.service.module.ai.agent.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.BizException;
import com.vben.service.common.redis.RedisKeys;
import com.vben.service.module.ai.tool.AiToolDef;
import com.vben.service.module.ai.tool.AiToolExecutor;
import com.vben.service.module.ai.tool.AiToolRegistry;
import com.vben.service.module.ai.tool.AiToolResult;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * AI 多步计划服务：模型提交计划后的服务端校验、顺序执行、高危步骤暂停/继续。
 *
 * <p>安全要点：①计划步骤的标题/高危标记/权限全部以注册中心实时数据为准，前端只回传
 * tool/args/reason；②每步执行前再次校验权限（确认后权限可能被收回）；③暂停态存 Redis，
 * key 绑定 userId，他人无法继续；④任一步失败即终止，不自动跳过。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPlanService {

  /** 高危步骤暂停态有效期：用户 10 分钟内未确认则计划作废 */
  private static final Duration STATE_TTL = Duration.ofMinutes(10);
  /** 单计划最多步骤数，防止模型产出超大计划 */
  private static final int MAX_STEPS = 12;

  private final AiToolRegistry registry;
  private final AiToolExecutor toolExecutor;
  private final ObjectMapper objectMapper;
  private final StringRedisTemplate redisTemplate;

  /**
   * 校验模型 submit_plan 提交的参数，转为服务端存储步骤。
   * 工具不存在/无权限/参数形态错误均抛 BizException，由编排循环回喂模型修正。
   */
  public List<PlanModels.StoredStep> validateSubmitted(JsonNode args, LoginUser user) {
    if (args == null || !args.isObject()) {
      throw BizException.badRequest("error.ai.plan.badArgs");
    }
    JsonNode stepsNode = args.get("steps");
    if (stepsNode == null || !stepsNode.isArray() || stepsNode.isEmpty()) {
      throw BizException.badRequest("error.ai.plan.empty");
    }
    if (stepsNode.size() > MAX_STEPS) {
      throw BizException.badRequest("error.ai.plan.tooMany", MAX_STEPS);
    }

    List<PlanModels.StoredStep> steps = new ArrayList<>();
    int index = 0;
    for (JsonNode node : stepsNode) {
      String tool = node.path("tool").asText(null);
      if (tool == null || tool.isBlank()) {
        throw BizException.badRequest("error.ai.plan.stepNoTool", index + 1);
      }
      AiToolDef def = registry.requireDef(tool);
      if (!registry.canUse(user, def)) {
        throw BizException.forbidden("error.ai.plan.stepNoPermission", def.title());
      }
      JsonNode stepArgs = node.get("args");
      if (stepArgs != null && !stepArgs.isObject()) {
        throw BizException.badRequest("error.ai.plan.stepBadArgs", def.title());
      }
      String reason = node.path("reason").asText("");
      steps.add(new PlanModels.StoredStep(index, tool, def.title(),
          stepArgs == null || stepArgs.isNull() ? "{}" : stepArgs.toString(),
          reason, def.danger()));
      index++;
    }
    return steps;
  }

  /**
   * 用户确认计划后开始执行（新 SSE）。客户端回传的步骤重新经注册中心校验，
   * 不使用模型/客户端给的标题与高危标记。
   */
  public void execute(String goal, JsonNode stepsNode, LoginUser user, AiPlanSink sink) {
    // 复用模型提交的校验入口：把客户端回传包装成 submit_plan 参数形态
    JsonNode wrapper = objectMapper.createObjectNode().set("steps", stepsNode);
    List<PlanModels.StoredStep> steps = validateSubmitted(wrapper, user);

    String planId = UUID.randomUUID().toString().replace("-", "");
    sink.started(planId, goal, steps);
    runFrom(planId, goal, steps, 0, new ArrayList<>(), user, sink);
  }

  /** 用户对高危步骤二次确认（或取消）后继续（新 SSE） */
  public void continuePlan(String planId, boolean confirmed, LoginUser user, AiPlanSink sink) {
    PlanModels.PlanState state = loadState(planId);
    if (!state.userId().equals(user.getUserId())) {
      throw BizException.badRequest("error.ai.plan.notOwner");
    }
    if (!confirmed) {
      redisTemplate.delete(RedisKeys.aiPlan(planId));
      sink.started(planId, state.goal(), state.steps());
      for (PlanModels.StepResult r : state.results()) {
        sink.stepDone(r.index(), r.title(), r.summary());
      }
      sink.done("cancelled", state.results());
      return;
    }
    sink.started(planId, state.goal(), state.steps());
    for (PlanModels.StepResult r : state.results()) {
      sink.stepDone(r.index(), r.title(), r.summary());
    }
    List<PlanModels.StepResult> results = new ArrayList<>(state.results());
    // 用户已对该危险步骤二次确认：先执行它，再从下一步继续
    // （否则 runFrom 从 nextIndex 恢复时会再次命中同一步的 danger 拦截而死循环）
    int nextIndex = state.nextIndex();
    if (nextIndex < 0 || nextIndex >= state.steps().size()) {
      redisTemplate.delete(RedisKeys.aiPlan(planId));
      sink.done("completed", results);
      return;
    }
    if (!runOne(state.steps().get(nextIndex), results, sink)) {
      redisTemplate.delete(RedisKeys.aiPlan(planId));
      sink.done("failed", results);
      return;
    }
    runFrom(planId, state.goal(), state.steps(), nextIndex + 1, results, user, sink);
  }

  // ------------------------------------------------------------------
  // 顺序执行：遇高危暂停；任一步失败终止
  // ------------------------------------------------------------------

  private void runFrom(String planId, String goal, List<PlanModels.StoredStep> steps,
      int fromIndex, List<PlanModels.StepResult> results, LoginUser user, AiPlanSink sink) {
    for (int i = fromIndex; i < steps.size(); i++) {
      PlanModels.StoredStep step = steps.get(i);

      // 高危步骤：暂停，等待二次确认
      if (step.danger()) {
        saveState(new PlanModels.PlanState(planId, user.getUserId(), goal, steps, i, results));
        sink.needConfirm(i, step.title(), step.reason());
        return;
      }

      if (!runOne(step, results, sink)) {
        redisTemplate.delete(RedisKeys.aiPlan(planId));
        sink.done("failed", results);
        return;
      }
    }
    redisTemplate.delete(RedisKeys.aiPlan(planId));
    sink.done("completed", results);
  }

  /** 执行单步；业务异常作为失败事件返回（false=计划终止） */
  private boolean runOne(PlanModels.StoredStep step, List<PlanModels.StepResult> results,
      AiPlanSink sink) {
    sink.stepStart(step.index(), step.title());
    try {
      // 执行前再次校验权限；LoginUserHolder 由 Web 层在工作线程设置
      AiToolDef def = registry.requireDef(step.tool());
      LoginUser current = LoginUserHolder.require();
      if (!registry.canUse(current, def)) {
        throw BizException.forbidden("error.ai.plan.stepNoPermission", def.title());
      }
      AiToolResult result = toolExecutor.runStrict(step.tool(), step.argsJson());
      String summary = result.summary() != null ? result.summary() : "完成";
      results.add(new PlanModels.StepResult(step.index(), step.title(), true, summary));
      sink.stepDone(step.index(), step.title(), summary);
      return true;
    } catch (BizException e) {
      results.add(new PlanModels.StepResult(step.index(), step.title(), false, e.getMessage()));
      sink.failed(step.index(), step.title(), e.getMessage());
      return false;
    }
  }

  // ------------------------------------------------------------------
  // Redis 暂停态
  // ------------------------------------------------------------------

  private void saveState(PlanModels.PlanState state) {
    try {
      redisTemplate.opsForValue().set(RedisKeys.aiPlan(state.planId()),
          objectMapper.writeValueAsString(state), STATE_TTL);
    } catch (Exception e) {
      throw new IllegalStateException("计划状态保存失败", e);
    }
  }

  private PlanModels.PlanState loadState(String planId) {
    String json = redisTemplate.opsForValue().get(RedisKeys.aiPlan(planId));
    if (json == null) {
      throw BizException.badRequest("error.ai.plan.expired");
    }
    try {
      return objectMapper.readValue(json, PlanModels.PlanState.class);
    } catch (Exception e) {
      redisTemplate.delete(RedisKeys.aiPlan(planId));
      throw BizException.badRequest("error.ai.plan.expired");
    }
  }
}
