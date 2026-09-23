package com.vben.service.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vben.service.module.auth.entity.RecoveryEmail;
import com.vben.service.module.system.entity.SysUser;
import org.apache.ibatis.annotations.Select;

public interface RecoveryEmailMapper extends BaseMapper<RecoveryEmail> {
  /** 邮箱变更和找回密码串行校验同一用户，防止检查后绑定或凭据被替换。 */
  @Select("SELECT * FROM sys_user WHERE id = #{userId} FOR UPDATE")
  SysUser lockUser(Long userId);
}
