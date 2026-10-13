package com.vben.service.module.ai.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vben.service.module.ai.kb.entity.KbEvalSet;
import org.apache.ibatis.annotations.Mapper;

/** 检索评测集 Mapper（必须置于 mapper 子包，见 @MapperScan 扫描规则） */
@Mapper
public interface KbEvalSetMapper extends BaseMapper<KbEvalSet> {
}
