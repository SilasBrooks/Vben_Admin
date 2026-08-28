package com.vben.service.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vben.service.module.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

  /** 用户角色 key 列表（status=0 的有效角色） */
  @Select("""
      SELECT r.role_key
      FROM sys_role r
      JOIN sys_user_role ur ON ur.role_id = r.id
      WHERE ur.user_id = #{userId} AND r.status = 0
      """)
  List<String> selectRoleKeysByUserId(Long userId);

  /** 用户已分配的角色 id 列表（不限角色状态，用于编辑回显） */
  @Select("""
      SELECT role_id
      FROM sys_user_role
      WHERE user_id = #{userId}
      """)
  List<Long> selectRoleIdsByUserId(Long userId);

  /** 统计拥有指定角色 key 的有效用户数（用于保护最后一个 super 用户不被删除/解绑） */
  @Select("""
      SELECT COUNT(1)
      FROM sys_user u
      JOIN sys_user_role ur ON ur.user_id = u.id
      JOIN sys_role r ON r.id = ur.role_id
      WHERE r.role_key = #{roleKey} AND u.status = 0
      """)
  long countActiveUsersByRoleKey(String roleKey);
}
