package com.vben.service.module.ai.llm;

import java.math.BigDecimal;

/**
 * 一次对话使用的 LLM 端点（OpenAI 兼容协议四要素 + 可选生成参数）。
 *
 * <p>由 {@link ActiveLlmResolver} 在每轮对话开始时解析一次，
 * 整次对话（含多轮工具调用）固定使用同一端点，不中途切换。
 */
public record LlmEndpoint(String baseUrl, String apiKey, String model, int timeoutSeconds,
                          BigDecimal temperature, Integer maxTokens) {

  public static LlmEndpoint from(SysLlmConfig cfg) {
    return new LlmEndpoint(cfg.getBaseUrl(), cfg.getApiKey(), cfg.getModel(),
        cfg.getTimeoutSeconds() == null ? 60 : cfg.getTimeoutSeconds(),
        cfg.getTemperature(), cfg.getMaxTokens());
  }

  /** API Key 是否已配置 */
  public boolean hasKey() {
    return apiKey != null && !apiKey.isBlank();
  }
}
