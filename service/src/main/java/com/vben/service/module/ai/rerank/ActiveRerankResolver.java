package com.vben.service.module.ai.rerank;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vben.service.common.BizException;
import com.vben.service.module.ai.rerank.mapper.SysRerankConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 激活 Rerank 解析器：知识库检索精排与具体配置解耦的唯一入口。
 *
 * <p>与 Embedding 不同，Rerank 是非必需增强能力：未配置/未激活/停用
 * 返回 null（调用方跳过精排层直接使用 RRF 融合结果），不抛异常。
 */
@Component
@RequiredArgsConstructor
public class ActiveRerankResolver {

  private final SysRerankConfigMapper mapper;

  /** 解析当前激活的 Rerank 端点；未配置/未激活/停用返回 null（精排层关闭） */
  public RerankEndpoint resolveOrNull() {
    SysRerankConfig cfg = mapper.selectOne(new QueryWrapper<SysRerankConfig>()
        .eq("is_active", 1)
        .eq("enabled", 1)
        .last("LIMIT 1"));
    return cfg == null ? null : RerankEndpoint.from(cfg);
  }
}
