package com.vben.service.module.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vben.service.module.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

  /**
   * 用户有权访问的全部菜单（含按钮），用于：
   * 1. 过滤出 M/C 类型构建动态路由树
   * 2. 汇总非空 perm 生成权限码列表
   */
  @Select("""
      SELECT DISTINCT m.*
      FROM sys_menu m
      JOIN sys_role_menu rm ON rm.menu_id = m.id
      JOIN sys_user_role ur ON ur.role_id = rm.role_id
      JOIN sys_role r ON r.id = ur.role_id
      WHERE ur.user_id = #{userId} AND m.status = 0 AND r.status = 0
      ORDER BY m.parent_id, m.order_num
      """)
  List<SysMenu> selectMenusByUserId(Long userId);
}
