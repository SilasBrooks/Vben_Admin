package com.vben.service.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vben.service.module.system.entity.SysMenu;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysMenuMapper;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证与权限聚合服务：把数据库中的用户-角色-菜单关系装配为 LoginUser
 */
@Service
@RequiredArgsConstructor
public class SysPermissionService {

  private final SysUserMapper userMapper;
  private final SysMenuMapper menuMapper;

  /** 按用户名查询有效用户（status=0） */
  public SysUser findActiveUser(String username) {
    return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
        .eq(SysUser::getUsername, username)
        .eq(SysUser::getStatus, 0));
  }

  public SysUser findActiveUserById(Long userId) {
    SysUser user = userMapper.selectById(userId);
    return user != null && user.getStatus() == 0 ? user : null;
  }

  /**
   * 装配完整 LoginUser（角色 + 权限码），以数据库实时数据为准
   */
  public LoginUser loadLoginUser(Long userId) {
    SysUser user = findActiveUserById(userId);
    if (user == null) {
      return null;
    }
    List<String> roles = userMapper.selectRoleKeysByUserId(userId);
    List<SysMenu> menus = menuMapper.selectMenusByUserId(userId);
    Set<String> permissions = menus.stream()
        .map(SysMenu::getPerm)
        .filter(p -> p != null && !p.isBlank())
        .collect(Collectors.toSet());
    return new LoginUser(user.getId(), user.getUsername(), roles, permissions);
  }
}
