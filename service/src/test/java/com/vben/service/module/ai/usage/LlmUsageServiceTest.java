package com.vben.service.module.ai.usage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.vben.service.module.ai.client.LlmUsage;
import com.vben.service.module.ai.usage.entity.SysLlmCallLog;
import com.vben.service.module.ai.usage.mapper.SysLlmCallLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** LLM 用量落库：字段映射与静默失败（落库失败不影响业务） */
class LlmUsageServiceTest {

  @Test
  void recordMapsAllUsageFields() {
    SysLlmCallLogMapper mapper = Mockito.mock(SysLlmCallLogMapper.class);
    LlmUsageService service = new LlmUsageService(mapper);

    service.record(LlmUsageService.SCENE_CHAT,
        new LlmUsage("deepseek-chat", 10L, 5L, 15L, 120L));

    ArgumentCaptor<SysLlmCallLog> captor = ArgumentCaptor.forClass(SysLlmCallLog.class);
    verify(mapper).insert(captor.capture());
    SysLlmCallLog entity = captor.getValue();
    assertEquals("chat", entity.getScene());
    assertEquals("deepseek-chat", entity.getModel());
    assertEquals(10L, entity.getPromptTokens());
    assertEquals(5L, entity.getCompletionTokens());
    assertEquals(15L, entity.getTotalTokens());
    assertEquals(120L, entity.getElapsedMs());
  }

  @Test
  void recordSwallowsInsertFailure() {
    SysLlmCallLogMapper mapper = Mockito.mock(SysLlmCallLogMapper.class);
    doThrow(new RuntimeException("db down")).when(mapper)
        .insert(Mockito.any(SysLlmCallLog.class));
    LlmUsageService service = new LlmUsageService(mapper);

    // 用量为旁路观测：落库异常 MUST NOT 外抛影响对话
    assertDoesNotThrow(() -> service.record(LlmUsageService.SCENE_SUMMARIZE,
        new LlmUsage("m", null, null, null, 5L)));
  }
}
