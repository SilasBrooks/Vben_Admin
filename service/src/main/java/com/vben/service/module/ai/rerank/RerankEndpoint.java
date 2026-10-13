package com.vben.service.module.ai.rerank;

/**
 * Rerank 调用端点（从激活配置解析出的不可变快照）。
 *
 * <p>每次检索时实时解析，模型/Key 切换即时生效，无需重启。
 */
public record RerankEndpoint(Long id, String baseUrl, String apiKey, String model,
                             int timeoutSeconds) {

  public static RerankEndpoint from(SysRerankConfig cfg) {
    return new RerankEndpoint(cfg.getId(), cfg.getBaseUrl(), cfg.getApiKey(),
        cfg.getModel(), cfg.getTimeoutSeconds() == null ? 30 : cfg.getTimeoutSeconds());
  }

  public boolean hasKey() {
    return apiKey != null && !apiKey.isBlank();
  }
}
