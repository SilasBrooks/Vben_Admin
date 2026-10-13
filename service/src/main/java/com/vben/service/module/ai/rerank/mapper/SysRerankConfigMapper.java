package com.vben.service.module.ai.rerank.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vben.service.module.ai.rerank.SysRerankConfig;
import org.apache.ibatis.annotations.Mapper;

/** Rerank 精排模型配置 Mapper（CRUD 均为 MyBatis-Plus 标准方法） */
@Mapper
public interface SysRerankConfigMapper extends BaseMapper<SysRerankConfig> {
}
