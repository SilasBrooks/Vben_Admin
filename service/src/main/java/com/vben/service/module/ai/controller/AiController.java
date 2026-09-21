package com.vben.service.module.ai.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.OperLog;
import com.vben.service.common.R;
import com.vben.service.common.ratelimit.RateLimit;
import com.vben.service.module.ai.agent.AiChatService;
import com.vben.service.module.ai.agent.AiChatSink;
import com.vben.service.module.ai.agent.PendingToolCall;
import com.vben.service.module.ai.agent.plan.AiPlanService;
import com.vben.service.module.ai.agent.plan.AiPlanSink;
import com.vben.service.module.ai.agent.plan.PlanModels;
import com.vben.service.module.ai.client.AiStreamCancelledException;
import com.vben.service.module.ai.client.AiUpstreamException;
import com.vben.service.module.ai.client.CancelToken;
import com.vben.service.module.ai.client.DeepMessage;
import com.vben.service.module.ai.client.ToolCall;
import com.vben.service.module.ai.tool.AiToolExecutor;
import com.vben.service.module.ai.tool.AiToolResult;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 智能助手接口：
 * <ul>
 *   <li>POST /ai/chat             SSE 流式对话（事件 delta/history/toolcall/plan/done/error）</li>
 *   <li>POST /ai/chat/summarize   会话滚动摘要（被窗口移出的历史与已有摘要合并）</li>
 *   <li>POST /ai/tool/execute     用户确认后执行单个写操作</li>
 *   <li>POST /ai/plan/execute     用户确认计划后顺序执行（SSE 步骤流，高危步骤暂停）</li>
 *   <li>POST /ai/plan/continue    高危步骤二次确认/取消后继续（SSE）</li>
 * </ul>
 * 均要求登录；细粒度权限在工具执行器内按权限码校验。
 */
@Slf4j
@RestController
@Tag(name = "AI 助手", description = "AI 对话（SSE 流式）、工具执行确认、多步计划执行、会话摘要")
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

  /** SSE 连接超时：单轮对话最长 120 秒 */
  private static final long SSE_TIMEOUT_MS = 120_000L;

  private final AiChatService chatService;
  private final AiPlanService planService;
  private final AiToolExecutor toolExecutor;
  private final ObjectMapper objectMapper;

  /** 对话请求（前端持有完整会话历史） */
  public record AiChatDto(List<AiMessageDto> messages) {
  }

  /** OpenAI 协议形态的消息 */
  public record AiMessageDto(String role, String content,
                             @JsonProperty("tool_calls") List<ToolCall> toolCalls,
                             @JsonProperty("tool_call_id") String toolCallId) {
  }

  /** 确认执行请求；args 为模型产出的参数对象 */
  public record ToolExecuteDto(String toolCallId, String toolName, JsonNode args) {
  }

  public record ToolExecuteVo(boolean ok, String summary) {
  }

  /** 计划确认执行请求（前端只回传工具/参数/理由；标题与高危标记服务端重新判定） */
  public record PlanExecuteDto(String goal, List<PlanStepDto> steps) {
  }

  public record PlanStepDto(String tool, JsonNode args, String reason) {
  }

  /** 高危步骤二次确认/取消 */
  public record PlanContinueDto(String planId, boolean confirmed) {
  }

  /** 摘要请求：priorSummary 为已有摘要（可空），messages 为被窗口移出的历史轮次 */
  public record SummarizeDto(String priorSummary, List<AiMessageDto> messages) {
  }

  public record SummarizeVo(String summary) {
  }

  @RateLimit(name = "ai:chat", limit = 10, windowSeconds = 60, scope = RateLimit.Scope.USER)
  @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter chat(@RequestBody AiChatDto dto) {
    // 在请求线程捕获登录用户，传播到异步工作线程（LoginUserHolder 是 ThreadLocal）
    com.vben.service.security.LoginUser currentUser =
        com.vben.service.security.LoginUserHolder.require();

    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
    CancelToken cancelToken = new CancelToken();
    emitter.onCompletion(cancelToken::cancel);
    emitter.onTimeout(() -> {
      cancelToken.cancel();
      emitter.complete();
    });
    emitter.onError(e -> cancelToken.cancel());

    List<DeepMessage> inbound = toDeepMessages(dto.messages());

    Thread worker = new Thread(() -> {
      com.vben.service.security.LoginUserHolder.set(currentUser);
      try {
        chatService.chat(inbound, new SseChatSink(emitter, objectMapper), cancelToken);
      } catch (AiStreamCancelledException e) {
        // 用户停止 / 连接断开：静默结束
        emitter.complete();
      } catch (AiUpstreamException e) {
        safeSend(emitter, "error", java.util.Map.of("message", e.getMessage()), objectMapper);
        emitter.complete();
      } catch (Exception e) {
        log.error("AI 对话处理异常", e);
        safeSend(emitter, "error",
            java.util.Map.of("message",
                com.vben.service.common.I18nMessage.get("error.ai.unavailable")),
            objectMapper);
        emitter.complete();
      } finally {
        com.vben.service.security.LoginUserHolder.clear();
      }
    }, "ai-chat-worker");
    worker.setDaemon(true);
    worker.start();

    return emitter;
  }

  /** 用户在确认卡片点击"确认执行"后落库；@OperLog 记录写操作审计 */
  @OperLog(module = "AI助手", description = "执行AI确认操作")
  @PostMapping("/tool/execute")
  public R<ToolExecuteVo> executeTool(@RequestBody ToolExecuteDto dto) {
    String argsJson = dto.args() == null ? "{}" : dto.args().toString();
    AiToolResult result = toolExecutor.runStrict(dto.toolName(), argsJson);
    return R.ok(new ToolExecuteVo(true, result.summary()));
  }

  /**
   * 用户在计划卡确认后顺序执行（SSE）：事件 plan_started/step_start/step_done/
   * step_need_confirm/step_failed/plan_done；遇高危步骤发 step_need_confirm 后结束本连接，
   * 前端二次确认后调 /ai/plan/continue。
   */
  @OperLog(module = "AI助手", description = "执行AI多步计划")
  @RateLimit(name = "ai:plan", limit = 6, windowSeconds = 60, scope = RateLimit.Scope.USER)
  @PostMapping(value = "/plan/execute", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter executePlan(@RequestBody PlanExecuteDto dto) {
    LoginUser currentUser = LoginUserHolder.require();
    List<PlanStepDto> stepDtos = dto.steps() == null ? List.of() : dto.steps();
    JsonNode stepsNode = objectMapper.valueToTree(stepDtos);
    String goal = dto.goal() == null ? "" : dto.goal();
    return startPlanSse(currentUser, emitter ->
        planService.execute(goal, stepsNode, currentUser, new SsePlanSink(emitter, objectMapper)));
  }

  /** 高危步骤二次确认（confirmed=true）或取消（false）后的继续执行（SSE） */
  @RateLimit(name = "ai:plan", limit = 6, windowSeconds = 60, scope = RateLimit.Scope.USER)
  @PostMapping(value = "/plan/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter continuePlan(@RequestBody PlanContinueDto dto) {
    LoginUser currentUser = LoginUserHolder.require();
    return startPlanSse(currentUser, emitter -> planService.continuePlan(dto.planId(), dto.confirmed(),
        currentUser, new SsePlanSink(emitter, objectMapper)));
  }

  /** 计划 SSE 公共启动逻辑：在请求线程捕获登录用户，传播到异步工作线程 */
  private SseEmitter startPlanSse(LoginUser currentUser,
      java.util.function.Consumer<SseEmitter> task) {
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
    emitter.onTimeout(emitter::complete);
    emitter.onError(e -> {
    });
    Thread worker = new Thread(() -> {
      LoginUserHolder.set(currentUser);
      try {
        task.accept(emitter);
      } catch (AiUpstreamException e) {
        safeSend(emitter, "plan_error", Map.of("message", e.getMessage()), objectMapper);
      } catch (com.vben.service.common.BizException e) {
        // 计划校验/权限拒绝/参数错误：把已翻译文案直接下发，前端可提示具体原因
        safeSend(emitter, "plan_error", Map.of("message", e.getMessage()), objectMapper);
      } catch (Exception e) {
        log.error("AI 计划执行异常", e);
        safeSend(emitter, "plan_error",
            Map.of("message", com.vben.service.common.I18nMessage.get("error.ai.unavailable")),
            objectMapper);
      } finally {
        emitter.complete();
        LoginUserHolder.clear();
      }
    }, "ai-plan-worker");
    worker.setDaemon(true);
    worker.start();
    return emitter;
  }

  /** 会话滚动摘要：把被窗口移出的历史与已有摘要合并为一份新摘要（同步返回） */
  @PostMapping("/chat/summarize")
  public R<SummarizeVo> summarize(@RequestBody SummarizeDto dto) {
    List<DeepMessage> inbound = toDeepMessages(dto.messages());
    return R.ok(new SummarizeVo(chatService.summarize(dto.priorSummary(), inbound)));
  }

  // ------------------------------------------------------------------

  private List<DeepMessage> toDeepMessages(List<AiMessageDto> dtos) {
    if (dtos == null) {
      return List.of();
    }
    List<DeepMessage> result = new ArrayList<>();
    for (AiMessageDto m : dtos) {
      if (m.role() == null) {
        continue;
      }
      switch (m.role()) {
        case "system" -> result.add(DeepMessage.system(m.content()));
        case "user" -> result.add(DeepMessage.user(m.content()));
        case "assistant" -> result.add(m.toolCalls() == null
            ? DeepMessage.assistant(m.content())
            : DeepMessage.assistantToolCalls(m.content(), m.toolCalls()));
        case "tool" -> result.add(DeepMessage.toolResult(m.toolCallId(), m.content()));
        default -> log.warn("忽略未知角色消息: {}", m.role());
      }
    }
    return result;
  }

  private static void safeSend(SseEmitter emitter, String event, Object payload,
                               ObjectMapper objectMapper) {
    try {
      String json = objectMapper.writeValueAsString(payload);
      // 已预序列化为 JSON，按 TEXT_PLAIN 原样写出，避免再被 Jackson 二次加引号
      emitter.send(SseEmitter.event().name(event).data(json, MediaType.TEXT_PLAIN));
    } catch (Exception ignored) {
      // 连接已断开等场景，忽略发送失败
    }
  }

  /** AiChatSink → SSE 适配 */
  private static class SseChatSink implements AiChatSink {

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;

    private SseChatSink(SseEmitter emitter, ObjectMapper objectMapper) {
      this.emitter = emitter;
      this.objectMapper = objectMapper;
    }

    @Override
    public void delta(String text) {
      safeSend(emitter, "delta", java.util.Map.of("text", text), objectMapper);
    }

    @Override
    public void history(List<DeepMessage> entries) {
      safeSend(emitter, "history", java.util.Map.of("messages", entries), objectMapper);
    }

    @Override
    public void toolCall(PendingToolCall call) {
      Object args;
      try {
        args = objectMapper.readValue(call.argsJson(), Object.class);
      } catch (Exception e) {
        args = call.argsJson();
      }
      safeSend(emitter, "toolcall", java.util.Map.of(
          "toolCallId", call.toolCallId(),
          "toolName", call.toolName(),
          "title", call.title(),
          "args", args), objectMapper);
    }

    @Override
    public void plan(String toolCallId, String goal, List<PlanModels.StoredStep> steps) {
      List<Object> stepViews = new ArrayList<>();
      for (PlanModels.StoredStep s : steps) {
        Map<String, Object> view = new java.util.LinkedHashMap<>(PlanModels.stepView(s));
        // args 在 stepView 中是 JSON 字符串，前端需要对象，尝试解析
        try {
          view.put("args", objectMapper.readValue(s.argsJson(), Object.class));
        } catch (Exception ignored) {
          view.put("args", s.argsJson());
        }
        stepViews.add(view);
      }
      safeSend(emitter, "plan", java.util.Map.of(
          "toolCallId", toolCallId,
          "goal", goal,
          "steps", stepViews), objectMapper);
    }

    @Override
    public void done() {
      safeSend(emitter, "done", java.util.Map.of(), objectMapper);
      emitter.complete();
    }

    @Override
    public void error(String message) {
      safeSend(emitter, "error", java.util.Map.of("message", message), objectMapper);
      emitter.complete();
    }
  }

  /** AiPlanSink → SSE 适配：计划执行步骤流（连接生命周期由 startPlanSse 统一管理） */
  private static class SsePlanSink implements AiPlanSink {

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;

    private SsePlanSink(SseEmitter emitter, ObjectMapper objectMapper) {
      this.emitter = emitter;
      this.objectMapper = objectMapper;
    }

    @Override
    public void started(String planId, String goal, List<PlanModels.StoredStep> steps) {
      List<Object> stepViews = new ArrayList<>();
      for (PlanModels.StoredStep s : steps) {
        Map<String, Object> view = new java.util.LinkedHashMap<>(PlanModels.stepView(s));
        try {
          view.put("args", objectMapper.readValue(s.argsJson(), Object.class));
        } catch (Exception ignored) {
          view.put("args", s.argsJson());
        }
        stepViews.add(view);
      }
      safeSend(emitter, "plan_started", Map.of(
          "planId", planId, "goal", goal, "steps", stepViews), objectMapper);
    }

    @Override
    public void stepStart(int index, String title) {
      safeSend(emitter, "step_start", Map.of("index", index, "title", title), objectMapper);
    }

    @Override
    public void stepDone(int index, String title, String summary) {
      safeSend(emitter, "step_done",
          Map.of("index", index, "title", title, "summary", summary), objectMapper);
    }

    @Override
    public void needConfirm(int index, String title, String reason) {
      safeSend(emitter, "step_need_confirm",
          Map.of("index", index, "title", title, "reason", reason == null ? "" : reason),
          objectMapper);
    }

    @Override
    public void failed(int index, String title, String error) {
      safeSend(emitter, "step_failed",
          Map.of("index", index, "title", title, "error", error), objectMapper);
    }

    @Override
    public void done(String status, List<PlanModels.StepResult> results) {
      safeSend(emitter, "plan_done", Map.of("status", status, "results", results), objectMapper);
    }
  }
}
