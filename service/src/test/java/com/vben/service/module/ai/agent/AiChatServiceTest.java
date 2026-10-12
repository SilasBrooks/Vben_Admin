package com.vben.service.module.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.module.ai.agent.plan.AiPlanService;
import com.vben.service.module.ai.agent.plan.PlanModels;
import com.vben.service.module.ai.client.CancelToken;
import com.vben.service.module.ai.client.DeepMessage;
import com.vben.service.module.ai.client.LlmClient;
import com.vben.service.module.ai.client.StreamHandler;
import com.vben.service.module.ai.config.AiProperties;
import com.vben.service.module.ai.skill.SkillManifestBuilder;
import com.vben.service.module.ai.tool.AiToolExecutor;
import com.vben.service.module.ai.tool.AiToolRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * AI 对话编排单测：聚焦系统提示词的技能清单渐进式注入——
 * 有启用技能时清单拼接到基础提示词之后；无技能时零注入，对话行为不变。
 */
class AiChatServiceTest {

  private final LlmClient llmClient = Mockito.mock(LlmClient.class);
  private final AiToolExecutor toolExecutor = Mockito.mock(AiToolExecutor.class);
  private final AiToolRegistry toolRegistry = Mockito.mock(AiToolRegistry.class);
  private final AiPlanService planService = Mockito.mock(AiPlanService.class);
  private final AiProperties properties = Mockito.mock(AiProperties.class);
  private final SkillManifestBuilder manifestBuilder = Mockito.mock(SkillManifestBuilder.class);
  private final AiChatService service = new AiChatService(llmClient, toolExecutor, toolRegistry,
      planService, properties, new ObjectMapper(), manifestBuilder);

  /** 纯文本回复的最小流式行为：吐出一段文本后结束本轮 */
  private void stubPlainReply() {
    Mockito.doAnswer(invocation -> {
      StreamHandler handler = invocation.getArgument(2);
      handler.onText("好的");
      return null;
    }).when(llmClient).streamChat(anyList(), anyList(), any(StreamHandler.class),
        any(CancelToken.class));
  }

  /** 只记录持久化消息的空 Sink */
  private static class NoopSink implements AiChatSink {
    final List<DeepMessage> history = new ArrayList<>();

    @Override
    public void delta(String text) {
    }

    @Override
    public void history(List<DeepMessage> entries) {
      history.addAll(entries);
    }

    @Override
    public void toolCall(PendingToolCall call) {
    }

    @Override
    public void plan(String toolCallId, String goal, List<PlanModels.StoredStep> steps) {
    }

    @Override
    public void citations(List<KbCitation> items) {
    }

    @Override
    public void done() {
    }

    @Override
    public void error(String message) {
    }
  }

  @SuppressWarnings("unchecked")
  private List<DeepMessage> capturedMessages() {
    ArgumentCaptor<List<DeepMessage>> captor = ArgumentCaptor.forClass(List.class);
    verify(llmClient).streamChat(captor.capture(), anyList(), any(StreamHandler.class),
        any(CancelToken.class));
    return captor.getValue();
  }

  @Test
  void enabledSkillsAreAppendedToSystemPrompt() {
    when(manifestBuilder.build()).thenReturn("## 可用技能\n- demo 演示：测试技能");
    stubPlainReply();

    service.chat(List.of(DeepMessage.user("写个公告")), new NoopSink(), new CancelToken());

    DeepMessage system = capturedMessages().get(0);
    assertThat(system.getRole()).isEqualTo("system");
    // 基础提示词与技能清单同时在场
    assertThat(system.getContent()).contains("智能助手").contains("## 可用技能");
  }

  @Test
  void noSkillsKeepsPromptUntouched() {
    when(manifestBuilder.build()).thenReturn("");
    stubPlainReply();

    NoopSink sink = new NoopSink();
    service.chat(List.of(DeepMessage.user("你好")), sink, new CancelToken());

    List<DeepMessage> messages = capturedMessages();
    DeepMessage system = messages.get(0);
    assertThat(system.getRole()).isEqualTo("system");
    assertThat(system.getContent()).doesNotContain("可用技能");
    // 历史消息原样保留在 system 之后
    assertThat(messages.get(1).getRole()).isEqualTo("user");
    assertThat(messages.get(1).getContent()).isEqualTo("你好");
  }
}
