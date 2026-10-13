package com.vben.service.module.ai.usage;

import com.vben.service.module.ai.client.LlmUsage;
import com.vben.service.module.ai.usage.entity.SysLlmCallLog;
import com.vben.service.module.ai.usage.mapper.SysLlmCallLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * LLM 调用用量落库：每次业务上游请求一条（chat / summarize），
 * 仪表盘 AI 用量数据源。ping 连通性测试非业务调用不记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmUsageService {

  /** scene：AI 助手对话 */
  public static final String SCENE_CHAT = "chat";

  /** scene：对话历史压缩 */
  public static final String SCENE_SUMMARIZE = "summarize";

  private final SysLlmCallLogMapper callLogMapper;

  /**
   * 单次上游请求用量落库（每次 streamChat 恰一条）。
   * 落库失败仅告警不外抛——用量统计为旁路观测，MUST NOT 影响对话结果返回。
   */
  public void record(String scene, LlmUsage usage) {
    SysLlmCallLog entity = new SysLlmCallLog();
    entity.setScene(scene);
    entity.setModel(usage.model());
    entity.setPromptTokens(usage.promptTokens());
    entity.setCompletionTokens(usage.completionTokens());
    entity.setTotalTokens(usage.totalTokens());
    entity.setElapsedMs(usage.elapsedMs());
    try {
      callLogMapper.insert(entity);
    } catch (Exception e) {
      log.warn("LLM 用量落库失败（不影响业务）: scene={} model={}", scene, usage.model(), e);
    }
  }
}
