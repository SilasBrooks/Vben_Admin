package com.vben.service.module.ai.emb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vben.service.common.BizException;
import com.vben.service.module.ai.emb.mapper.SysEmbeddingConfigMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Embedding 模型配置服务单测：不启动容器，mock Mapper 与 EmbeddingClient，
 * 重点验证 Key 脱敏、名称唯一、编辑留空不覆盖 Key、全局唯一激活与激活行禁删、
 * 连通测试实测维度回填。
 */
class SysEmbeddingConfigServiceTest {

  private final SysEmbeddingConfigMapper mapper = Mockito.mock(SysEmbeddingConfigMapper.class);
  private final EmbeddingClient embeddingClient = Mockito.mock(EmbeddingClient.class);
  private final SysEmbeddingConfigService service =
      new SysEmbeddingConfigService(mapper, embeddingClient);

  private SysEmbeddingConfig cfg(Long id, String name, int isActive, int enabled) {
    SysEmbeddingConfig c = new SysEmbeddingConfig();
    c.setId(id);
    c.setName(name);
    c.setBaseUrl("https://api.example.com/");
    c.setApiKey("sk-1234567890abcd");
    c.setModel("bge-m3");
    c.setEnabled(enabled);
    c.setIsActive(isActive);
    return c;
  }

  // ------------------------------------------------------------------
  // 脱敏
  // ------------------------------------------------------------------

  @Test
  void maskKeyHandlesBoundaries() {
    assertThat(SysEmbeddingConfigService.maskKey(null)).isEmpty();
    assertThat(SysEmbeddingConfigService.maskKey("")).isEmpty();
    assertThat(SysEmbeddingConfigService.maskKey("  ")).isEmpty();
    assertThat(SysEmbeddingConfigService.maskKey("abc")).isEqualTo("***");
    assertThat(SysEmbeddingConfigService.maskKey("abcdefghij")).isEqualTo("**********");
    assertThat(SysEmbeddingConfigService.maskKey("sk-1234567890abcd"))
        .isEqualTo("sk-12****abcd");
  }

  // ------------------------------------------------------------------
  // 新增：名称唯一 + 归一化
  // ------------------------------------------------------------------

  @Test
  void createRejectsDuplicateName() {
    when(mapper.selectCount(any())).thenReturn(1L);
    assertThatThrownBy(() -> service.create(cfg(null, "硅基流动", 0, 1)))
        .isInstanceOf(BizException.class);
  }

  @Test
  void createNormalizesFields() {
    when(mapper.selectCount(any())).thenReturn(0L);
    SysEmbeddingConfig input = cfg(null, "硅基流动", 1, 1);
    input.setApiKey(null);
    when(mapper.selectById(any())).thenReturn(input);

    service.create(input);

    assertThat(input.getBaseUrl()).isEqualTo("https://api.example.com");
    assertThat(input.getIsActive()).isZero();
    // 维度只能由连通测试实测回填
    assertThat(input.getDimension()).isNull();
    assertThat(input.getApiKey()).isEmpty();
    verify(mapper).insert(input);
  }

  @Test
  void createRejectsMissingRequiredFields() {
    SysEmbeddingConfig input = new SysEmbeddingConfig();
    input.setName("x");
    assertThatThrownBy(() -> service.create(input)).isInstanceOf(BizException.class);
  }

  // ------------------------------------------------------------------
  // 协议风格：空值回落 standard、合法值透传、非法值拒绝
  // ------------------------------------------------------------------

  @Test
  void createDefaultsProtocolToStandardAndPassesArkMultimodal() {
    when(mapper.selectCount(any())).thenReturn(0L);
    SysEmbeddingConfig input = cfg(null, "方舟vision", 0, 1);
    input.setProtocol(null);
    when(mapper.selectById(any())).thenReturn(input);
    service.create(input);
    assertThat(input.getProtocol()).isEqualTo(SysEmbeddingConfig.PROTOCOL_STANDARD);

    SysEmbeddingConfig ark = cfg(null, "方舟vision2", 0, 1);
    ark.setProtocol("ark-multimodal");
    when(mapper.selectById(any())).thenReturn(ark);
    service.create(ark);
    assertThat(ark.getProtocol()).isEqualTo(SysEmbeddingConfig.PROTOCOL_ARK_MULTIMODAL);
    verify(mapper).insert(ark);
  }

  @Test
  void createRejectsUnknownProtocol() {
    SysEmbeddingConfig input = cfg(null, "bad", 0, 1);
    input.setProtocol("grpc");
    assertThatThrownBy(() -> service.create(input)).isInstanceOf(BizException.class);
    verify(mapper, never()).insert(any(SysEmbeddingConfig.class));
  }

  @Test
  void updateNormalizesProtocol() {
    SysEmbeddingConfig exists = cfg(7L, "旧", 0, 1);
    when(mapper.selectById(7L)).thenReturn(exists);
    when(mapper.selectCount(any())).thenReturn(0L);

    SysEmbeddingConfig patch = cfg(7L, "旧", 0, 1);
    patch.setProtocol(" ark-multimodal ");
    service.update(7L, patch);
    assertThat(exists.getProtocol()).isEqualTo(SysEmbeddingConfig.PROTOCOL_ARK_MULTIMODAL);
  }

  // ------------------------------------------------------------------
  // 编辑：空 key 不覆盖 + 旧维度失效
  // ------------------------------------------------------------------

  @Test
  void updateKeepsApiKeyWhenBlankAndResetsDimension() {
    SysEmbeddingConfig exists = cfg(5L, "旧名", 0, 1);
    exists.setDimension(1024);
    when(mapper.selectById(5L)).thenReturn(exists);
    when(mapper.selectCount(any())).thenReturn(0L);

    SysEmbeddingConfig patch = cfg(5L, "新名", 0, 1);
    patch.setApiKey("");

    service.update(5L, patch);

    assertThat(exists.getName()).isEqualTo("新名");
    assertThat(exists.getApiKey()).isEqualTo("sk-1234567890abcd");
    // 模型/地址可能已变更，旧维度必须失效等待重测
    assertThat(exists.getDimension()).isNull();
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
  // 连通测试：实测维度回填
  // ------------------------------------------------------------------

  @Test
  void pingDelegatesToClientAndBackfillsDimension() {
    SysEmbeddingConfig exists = cfg(6L, "p", 0, 1);
    when(mapper.selectById(6L)).thenReturn(exists);
    when(embeddingClient.embedOne(any(), any())).thenReturn(new float[1024]);

    Map<String, Object> data = service.ping(6L);

    assertThat(data.get("ok")).isEqualTo(true);
    assertThat(data.get("dimension")).isEqualTo(1024);
    assertThat(exists.getDimension()).isEqualTo(1024);
    verify(mapper).updateById(exists);
  }

  // ------------------------------------------------------------------
  // 不存在的配置
  // ------------------------------------------------------------------

  @Test
  void operationsOnMissingConfigFail() {
    when(mapper.selectById(any())).thenReturn(null);
    assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(BizException.class);
    assertThatThrownBy(() -> service.activate(99L)).isInstanceOf(BizException.class);
    assertThatThrownBy(() -> service.update(99L, cfg(99L, "x", 0, 1)))
        .isInstanceOf(BizException.class);
    assertThatThrownBy(() -> service.ping(99L)).isInstanceOf(BizException.class);
    verify(mapper, never()).deleteById(eq(99L));
  }
}
