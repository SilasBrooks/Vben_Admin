package com.vben.service.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vben.service.module.system.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

  /** 查询某角色下全部用户 id（角色授权变更时逐人发通知用） */
  @Select("SELECT user_id FROM sys_user_role WHERE role_id = #{roleId}")
  List<Long> selectUserIdsByRoleId(Long roleId);
}
