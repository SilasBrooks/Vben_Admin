package com.vben.service.module.ai.agent;

import com.vben.service.module.ai.client.AiUpstreamException;
import com.vben.service.module.ai.client.CancelToken;
import com.vben.service.module.ai.client.DeepMessage;
import com.vben.service.module.ai.client.DeepSeekClient;
import com.vben.service.module.ai.client.StreamHandler;
import com.vben.service.module.ai.client.ToolCall;
import com.vben.service.module.ai.config.AiProperties;
import com.vben.service.module.ai.tool.AiToolDef;
import com.vben.service.module.ai.tool.AiToolExecutor;
import com.vben.service.module.ai.tool.AiToolResult;
import com.vben.service.module.ai.tool.AiToolKind;
import com.vben.service.module.ai.tool.AiTools;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Agent 编排：在无状态后端上驱动「模型 ↔ 工具」循环。
 *
 * <ul>
 *   <li>查询工具：同请求内自动执行，结果作为 role=tool 消息回喂，模型继续作答</li>
 *   <li>新增工具：不执行，下发确认卡片后结束本轮；用户确认由前端再次发起请求驱动</li>
 *   <li>每轮产生的消息通过 {@link AiChatSink#history} 让前端持久化，保证下轮请求协议完整</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AiChatService {

  /** 单条用户消息最大字符数 */
  private static final int MAX_MESSAGE_CHARS = 1000;
  /**
   * 回传历史消息条数兜底上限（正常由前端窗口保证 ≤50 并按轮截断，
   * 此处仅作服务端防线，略大于前端窗口；裁剪时清理孤立 tool 消息保证协议配对）。
   */
  private static final int MAX_HISTORY_MESSAGES = 60;

  private static final String SYSTEM_PROMPT = """
      你是 sgy 管理系统内置的智能助手，通过对话帮助用户完成系统管理操作。

      你的能力范围：
      1. 查询用户、角色、部门、菜单信息（使用 query_* 工具，系统会自动执行并把真实数据返回给你）
      2. 新增用户、角色、部门（使用 create_* 工具，系统会弹出确认框让用户确认后才真正创建）
      3. 给角色分配菜单授权（使用 assign_role_menus 工具，同样需要用户确认）

      工作规则：
      - 只能基于工具返回的真实数据回答，绝不编造 id、部门、角色、用户或菜单
      - 创建类操作信息不全时（如缺少用户名、部门名称不明确），先向用户追问，不要猜测或使用占位值
      - 菜单授权是全量替换（会清除该角色原有授权）：分配前必须先与用户确认完整菜单清单，并先用查询菜单工具核对菜单的准确名称
      - 工具返回"权限不足"时，直接告知用户"你的权限不足"，并简要说明缺少的权限，不要重复尝试该操作
      - 工具返回"未找到/参数错误"时，用简洁中文向用户解释原因并给出下一步建议
      - 回答使用简洁中文纯文本，可用短横线列举，不要使用 markdown 表格，不要输出 JSON
      - 与系统管理无关的问题，简短说明你只负责系统管理助手的工作
      - 任何场景都必须全程使用中文（包括调用工具前的说明文字），禁止输出英文句子或英文开场白
      """;

  private final DeepSeekClient deepSeekClient;
  private final AiToolExecutor toolExecutor;
  private final AiProperties properties;

  /**
   * 执行一轮对话（可能内部包含多轮工具调用）。
   *
   * @param inbound 前端回传的历史消息（不含 system 消息）
   */
  public void chat(List<DeepMessage> inbound, AiChatSink sink, CancelToken cancel) {
    List<DeepMessage> messages = new ArrayList<>();
    messages.add(DeepMessage.system(SYSTEM_PROMPT));
    messages.addAll(sanitize(inbound));

    int toolRounds = 0;
    while (true) {
      // 超过自动工具轮数上限后不再下发工具，强制模型基于已有信息作答
      boolean toolsAvailable = toolRounds < properties.getMaxToolRounds();
      List<java.util.Map<String, Object>> tools =
          toolsAvailable ? AiTools.schemas() : List.of();

      StringBuilder contentBuffer = new StringBuilder();
      List<ToolCall> calls = new ArrayList<>();

      deepSeekClient.streamChat(messages, tools, new StreamHandler() {
        @Override
        public void onText(String delta) {
          contentBuffer.append(delta);
          sink.delta(delta);
        }

        @Override
        public void onToolCalls(List<ToolCall> toolCalls) {
          calls.addAll(toolCalls);
        }
      }, cancel);

      // 本轮助手消息入栈（纯文本 或 文本+工具调用）
      String text = contentBuffer.toString();
      DeepMessage assistantMessage = calls.isEmpty()
          ? DeepMessage.assistant(text)
          : DeepMessage.assistantToolCalls(text.isEmpty() ? null : text, calls);
      messages.add(assistantMessage);
      sink.history(List.of(assistantMessage));

      // 纯文本回复 → 本轮结束
      if (calls.isEmpty()) {
        sink.done();
        return;
      }

      toolRounds++;
      LoginUser currentUser = LoginUserHolder.get();
      List<DeepMessage> autoResults = new ArrayList<>();
      boolean hasCreate = false;
      for (ToolCall call : calls) {
        AiToolDef def = AiTools.require(call.function().name());
        if (def.kind() == AiToolKind.QUERY) {
          AiToolResult result = toolExecutor.runQuiet(call.function().name(),
              call.function().arguments());
          autoResults.add(DeepMessage.toolResult(call.id(), result.content()));
        } else if (currentUser == null || !currentUser.hasPermission(def.permission())) {
          // 无权限：不下发确认卡片，把权限不足回喂模型，由模型直接告知用户
          autoResults.add(DeepMessage.toolResult(call.id(),
              "权限不足：当前账号没有「" + def.title() + "」的权限"));
        } else {
          hasCreate = true;
          sink.toolCall(new PendingToolCall(
              call.id(), call.function().name(), def.title(), call.function().arguments()));
        }
      }
      if (!autoResults.isEmpty()) {
        messages.addAll(autoResults);
        sink.history(autoResults);
      }

      // 存在待确认的写操作：暂停循环，等前端确认后再次发起请求
      if (hasCreate) {
        sink.done();
        return;
      }
      // 仅查询工具：带着结果继续下一轮模型调用
    }
  }

  /**
   * 会话滚动摘要：把「已有摘要 + 本批被移出窗口的历史」合并为一份新摘要（同步调用）。
   * 由前端在历史超窗时调用；服务端不保存任何会话状态。
   */
  public String summarize(String priorSummary, List<DeepMessage> history) {
    StringBuilder prompt = new StringBuilder();
    if (priorSummary != null && !priorSummary.isBlank()) {
      prompt.append("【已有摘要】\n").append(priorSummary.trim()).append("\n\n");
    }
    prompt.append("【新增对话】\n");
    List<DeepMessage> list = history == null ? List.of() : history;
    // 超长兜底：最多取最近 80 条进入摘要，控制成本
    int skipped = Math.max(0, list.size() - MAX_SUMMARY_MESSAGES);
    if (skipped > 0) {
      prompt.append("（更早的 ").append(skipped).append(" 条已略）\n");
    }
    for (int i = skipped; i < list.size(); i++) {
      DeepMessage m = list.get(i);
      String content = m.getContent() == null ? "" : m.getContent();
      switch (m.getRole()) {
        case "user" -> prompt.append("用户：").append(truncate(content, 500)).append('\n');
        case "assistant" -> {
          if (m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (ToolCall tc : m.getToolCalls()) {
              if (names.length() > 0) {
                names.append('、');
              }
              names.append(tc.function().name());
            }
            prompt.append("助手：[调用工具 ").append(names).append("]\n");
          } else {
            prompt.append("助手：").append(truncate(content, 500)).append('\n');
          }
        }
        case "tool" -> prompt.append("工具结果：").append(truncate(content, 120)).append('\n');
        default -> {
          // 忽略 system 等其他角色
        }
      }
    }

    StringBuilder summary = new StringBuilder();
    deepSeekClient.streamChat(
        List.of(DeepMessage.system(SUMMARY_SYSTEM_PROMPT), DeepMessage.user(prompt.toString())),
        List.of(),
        new StreamHandler() {
          @Override
          public void onText(String delta) {
            summary.append(delta);
          }

          @Override
          public void onToolCalls(List<ToolCall> toolCalls) {
            // 摘要请求不提供工具，不会触发
          }
        },
        new CancelToken());
    String result = summary.toString().trim();
    if (result.isEmpty()) {
      throw new AiUpstreamException("AI 摘要生成失败，请稍后重试");
    }
    return result;
  }

  /** 摘要系统提示词：合并式压缩，只保留结论性信息 */
  private static final String SUMMARY_SYSTEM_PROMPT = """
      你是管理后台 AI 助手的会话摘要器。把给定对话与已有摘要合并为一份不超过 300 字的中文摘要，
      保留：用户关注的对象与目标、已执行的操作及其结果、关键参数（如用户/角色/菜单名称）、未完成事项。
      只输出摘要正文，不要任何解释、前缀或格式标记；禁止编造对话中不存在的信息。
      """;

  /** 摘要输入最多包含的消息条数 */
  private static final int MAX_SUMMARY_MESSAGES = 80;

  private static String truncate(String text, int max) {
    return text.length() <= max ? text : text.substring(0, max) + "…";
  }

  /**
   * 历史消息兜底裁剪：超长截断；只保留最近 N 条；
   * 裁掉会破坏 tool_calls/tool 配对关系的头部消息（OpenAI 协议要求二者成对出现）。
   */
  private List<DeepMessage> sanitize(List<DeepMessage> inbound) {
    if (inbound == null) {
      return List.of();
    }
    List<DeepMessage> list = new ArrayList<>(inbound);
    // 单条超长截断
    for (DeepMessage m : list) {
      if ("user".equals(m.getRole()) && m.getContent() != null
          && m.getContent().length() > MAX_MESSAGE_CHARS) {
        // DeepMessage 不可变字段，用新实例替换
        list.set(list.indexOf(m), DeepMessage.user(m.getContent().substring(0, MAX_MESSAGE_CHARS)));
      }
    }
    // 保留最近 N 条
    if (list.size() > MAX_HISTORY_MESSAGES) {
      list = new ArrayList<>(list.subList(list.size() - MAX_HISTORY_MESSAGES, list.size()));
    }
    // 头部若是孤立的 tool 结果，删除（其 assistant(tool_calls) 已被裁掉）
    while (!list.isEmpty() && "tool".equals(list.get(0).getRole())) {
      list.remove(0);
    }
    // 头部若是 assistant(tool_calls) 但配对的 tool 结果不完整，连带删除该 assistant
    // 及其后连续的 tool 消息
    if (!list.isEmpty()) {
      DeepMessage first = list.get(0);
      if ("assistant".equals(first.getRole()) && first.getToolCalls() != null) {
        list.remove(0);
        while (!list.isEmpty() && "tool".equals(list.get(0).getRole())) {
          list.remove(0);
        }
      }
    }
    return list;
  }
}
