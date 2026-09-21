package com.vben.service.module.ai.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vben.service.module.ai.llm.SysLlmConfig;
import org.apache.ibatis.annotations.Mapper;

/** LLM 模型配置 Mapper（包名对齐 @MapperScan 的 **.mapper 规则） */
@Mapper
public interface SysLlmConfigMapper extends BaseMapper<SysLlmConfig> {
}
