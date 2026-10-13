package com.vben.service.module.ai.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vben.service.module.ai.kb.entity.KbEvalRun;
import org.apache.ibatis.annotations.Mapper;

/** 检索评测运行记录 Mapper（必须置于 mapper 子包，见 @MapperScan 扫描规则） */
@Mapper
public interface KbEvalRunMapper extends BaseMapper<KbEvalRun> {
}
