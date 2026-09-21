package com.vben.service.module.ai.llm;

import com.vben.service.common.BizException;
import com.vben.service.module.ai.client.LlmClient;
import com.vben.service.module.ai.llm.mapper.SysLlmConfigMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LLM 模型配置服务单测：不启动容器，mock Mapper 与 LlmClient，
 * 重点验证 Key 脱敏、名称唯一、全局唯一激活与激活行禁删。
 */
class LlmConfigServiceTest {

  private final SysLlmConfigMapper mapper = Mockito.mock(SysLlmConfigMapper.class);
  private final LlmClient llmClient = Mockito.mock(LlmClient.class);
  private final SysLlmConfigService service = new SysLlmConfigService(mapper, llmClient);

  private SysLlmConfig cfg(Long id, String name, int isActive, int enabled) {
    SysLlmConfig c = new SysLlmConfig();
    c.setId(id);
    c.setName(name);
    c.setBaseUrl("https://api.example.com/");
    c.setApiKey("sk-1234567890abcd");
    c.setModel("test-model");
    c.setEnabled(enabled);
    c.setIsActive(isActive);
    return c;
  }

  // ------------------------------------------------------------------
  // 脱敏
  // ------------------------------------------------------------------

  @Test
  void maskKeyHandlesBoundaries() {
    assertThat(SysLlmConfigService.maskKey(null)).isEmpty();
    assertThat(SysLlmConfigService.maskKey("")).isEmpty();
    assertThat(SysLlmConfigService.maskKey("  ")).isEmpty();
    assertThat(SysLlmConfigService.maskKey("abc")).isEqualTo("***");
    assertThat(SysLlmConfigService.maskKey("abcdefghij")).isEqualTo("**********");
    assertThat(SysLlmConfigService.maskKey("sk-1234567890abcd"))
        .isEqualTo("sk-12****abcd");
  }

  // ------------------------------------------------------------------
  // 新增：名称唯一 + 归一化
  // ------------------------------------------------------------------

  @Test
  void createRejectsDuplicateName() {
    when(mapper.selectCount(any())).thenReturn(1L);
    assertThatThrownBy(() -> service.create(cfg(null, "DeepSeek 生产", 0, 1)))
        .isInstanceOf(BizException.class);
  }

  @Test
  void createNormalizesFieldsAndDeactivates() {
    when(mapper.selectCount(any())).thenReturn(0L);
    SysLlmConfig input = cfg(null, "DeepSeek 生产", 1, 1);
    input.setApiKey(null);
    when(mapper.selectById(any())).thenReturn(input);

    service.create(input);

    assertThat(input.getBaseUrl()).isEqualTo("https://api.example.com");
    assertThat(input.getIsActive()).isZero();
    assertThat(input.getApiKey()).isEmpty();
    verify(mapper).insert(input);
  }

  @Test
  void createRejectsMissingRequiredFields() {
    SysLlmConfig input = new SysLlmConfig();
    input.setName("x");
    assertThatThrownBy(() -> service.create(input)).isInstanceOf(BizException.class);
  }

  // ------------------------------------------------------------------
  // 编辑：空 key 不覆盖
  // ------------------------------------------------------------------

  @Test
  void updateKeepsApiKeyWhenBlank() {
    SysLlmConfig exists = cfg(5L, "旧名", 0, 1);
    when(mapper.selectById(5L)).thenReturn(exists);
    when(mapper.selectCount(any())).thenReturn(0L);

    SysLlmConfig patch = cfg(5L, "新名", 0, 1);
    patch.setApiKey("");

    service.update(5L, patch);

    assertThat(exists.getName()).isEqualTo("新名");
    assertThat(exists.getApiKey()).isEqualTo("sk-1234567890abcd");
    verify(mapper).updateById(exists);
  }

  // ------------------------------------------------------------------
  // 删除：激活行禁止
  // ------------------------------------------------------------------

  @Test
  void deleteRejectsActiveConfig() {
    when(mapper.selectById(1L)).thenReturn(cfg(1L, "active", 1, 1));
    assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BizException.class);
    verify(mapper, never()).deleteById(1L);
  }

  @Test
  void deleteAllowsInactiveConfig() {
    when(mapper.selectById(2L)).thenReturn(cfg(2L, "idle", 0, 1));
    service.delete(2L);
    verify(mapper).deleteById(2L);
  }

  // ------------------------------------------------------------------
  // 激活：停用拒绝 + 全局互斥
  // ------------------------------------------------------------------

  @Test
  void activateRejectsDisabledConfig() {
    when(mapper.selectById(3L)).thenReturn(cfg(3L, "off", 0, 0));
    assertThatThrownBy(() -> service.activate(3L)).isInstanceOf(BizException.class);
    verify(mapper, never()).update(any(), any());
  }

  @Test
  void activateClearsOthersThenSetsTarget() {
    when(mapper.selectById(4L)).thenReturn(cfg(4L, "target", 0, 1));
    service.activate(4L);
    // 两次 update（全表清零 + 置目标行），均不携带实体（UpdateWrapper set 模式）
    verify(mapper, Mockito.times(2)).update(Mockito.isNull(), any());
  }

  // ------------------------------------------------------------------
  // 连通测试：透传 LlmClient 结果
  // ------------------------------------------------------------------

  @Test
  void pingDelegatesToClient() {
    when(mapper.selectById(6L)).thenReturn(cfg(6L, "p", 0, 1));
    when(llmClient.ping(any()))
        .thenReturn(new LlmClient.PingResult("test-model", 123, "pong"));

    var data = service.ping(6L);

    assertThat(data.get("ok")).isEqualTo(true);
    assertThat(data.get("elapsedMs")).isEqualTo(123L);
    assertThat(data.get("model")).isEqualTo("test-model");
  }

  @Test
  void operationsOnMissingConfigFail() {
    when(mapper.selectById(any())).thenReturn(null);
    assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(BizException.class);
    assertThatThrownBy(() -> service.activate(99L)).isInstanceOf(BizException.class);
    assertThatThrownBy(() -> service.update(99L, cfg(99L, "x", 0, 1)))
        .isInstanceOf(BizException.class);
    verify(mapper, never()).deleteById(eq(99L));
  }
}
