package com.vben.service.module.ai.llm;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vben.service.module.ai.config.AiProperties;
import com.vben.service.module.ai.llm.mapper.SysLlmConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 激活 LLM 解析器：AI 调用链路与具体模型配置解耦的唯一入口。
 *
 * <p>每次对话开始查询一次（低频，不做缓存，配置变更即时生效）：
 * 命中 {@code is_active=1 AND enabled=1} 的配置则使用之；否则回退
 * application.yml 的 deepseek.* 兜底配置（存量行为不破坏）。
 */
@Component
@RequiredArgsConstructor
public class ActiveLlmResolver {

  private final SysLlmConfigMapper mapper;
  private final AiProperties fallback;

  /** 解析当前应使用的 LLM 端点（激活配置优先，yml 兜底） */
  public LlmEndpoint resolve() {
    SysLlmConfig cfg = mapper.selectOne(new QueryWrapper<SysLlmConfig>()
        .eq("is_active", 1)
        .eq("enabled", 1)
        .last("LIMIT 1"));
    if (cfg != null) {
      return LlmEndpoint.from(cfg);
    }
    return new LlmEndpoint(fallback.getBaseUrl(), fallback.getApiKey(), fallback.getModel(),
        fallback.getTimeoutSeconds(), null, null);
  }
}
