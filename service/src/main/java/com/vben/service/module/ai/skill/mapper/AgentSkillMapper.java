package com.vben.service.module.ai.skill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vben.service.module.ai.skill.AgentSkill;
import org.apache.ibatis.annotations.Mapper;

/** Agent 技能 Mapper（必须置于 mapper 子包，见 @MapperScan 扫描规则） */
@Mapper
public interface AgentSkillMapper extends BaseMapper<AgentSkill> {
}
