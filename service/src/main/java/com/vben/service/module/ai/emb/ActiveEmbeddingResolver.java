package com.vben.service.module.ai.emb;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vben.service.common.BizException;
import com.vben.service.module.ai.emb.mapper.SysEmbeddingConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 激活 Embedding 解析器：知识库向量化/检索与具体配置解耦的唯一入口。
 *
 * <p>与对话模型不同，Embedding 无 yml 兜底（不同厂商协议差异大且非必需能力）：
 * 未配置或未激活时抛出可读业务错误，由调用方决定提示方式。
 */
@Component
@RequiredArgsConstructor
public class ActiveEmbeddingResolver {

  private final SysEmbeddingConfigMapper mapper;

  /** 解析当前激活的 Embedding 端点；未配置/未激活/停用均返回 null（调用方负责提示） */
  public EmbEndpoint resolveOrNull() {
    SysEmbeddingConfig cfg = mapper.selectOne(new QueryWrapper<SysEmbeddingConfig>()
        .eq("is_active", 1)
        .eq("enabled", 1)
        .last("LIMIT 1"));
    return cfg == null ? null : EmbEndpoint.from(cfg);
  }

  /** 解析当前激活的 Embedding 端点；未配置时抛出引导配置的可读错误 */
  public EmbEndpoint requireActive() {
    EmbEndpoint endpoint = resolveOrNull();
    if (endpoint == null) {
      throw BizException.badRequest("error.ai.emb.notActive");
    }
    if (!endpoint.hasKey()) {
      throw BizException.badRequest("error.ai.emb.noKey");
    }
    return endpoint;
  }
}
