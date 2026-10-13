package com.vben.service.module.ai.usage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vben.service.module.ai.usage.entity.SysLlmCallLog;
import org.apache.ibatis.annotations.Mapper;

/** LLM 调用用量日志 Mapper（必须置于 mapper 子包，见 @MapperScan 扫描规则） */
@Mapper
public interface SysLlmCallLogMapper extends BaseMapper<SysLlmCallLog> {
}
