package com.vben.service.module.ai.emb;

/**
 * 一次向量化调用使用的 Embedding 端点要素（协议风格 + 连接信息）。
 *
 * <p>由 {@link ActiveEmbeddingResolver} 在每次向量化/检索开始时解析，
 * 同一批文本固定使用同一端点。
 */
public record EmbEndpoint(String baseUrl, String apiKey, String model, String protocol,
                          int timeoutSeconds) {

  public static EmbEndpoint from(SysEmbeddingConfig cfg) {
    return new EmbEndpoint(cfg.getBaseUrl(), cfg.getApiKey(), cfg.getModel(),
        cfg.getProtocol(), cfg.getTimeoutSeconds() == null ? 60 : cfg.getTimeoutSeconds());
  }

  /** API Key 是否已配置 */
  public boolean hasKey() {
    return apiKey != null && !apiKey.isBlank();
  }

  /** 是否方舟多模态协议（NULL/未知值回落标准协议，存量行兼容） */
  public boolean arkMultimodal() {
    return SysEmbeddingConfig.PROTOCOL_ARK_MULTIMODAL.equals(protocol);
  }
}
