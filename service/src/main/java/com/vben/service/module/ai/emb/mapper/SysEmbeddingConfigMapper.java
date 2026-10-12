package com.vben.service.module.ai.emb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vben.service.module.ai.emb.SysEmbeddingConfig;
import org.apache.ibatis.annotations.Mapper;

/** Embedding 模型配置 Mapper（必须置于 mapper 子包，见 @MapperScan 扫描规则） */
@Mapper
public interface SysEmbeddingConfigMapper extends BaseMapper<SysEmbeddingConfig> {
}
