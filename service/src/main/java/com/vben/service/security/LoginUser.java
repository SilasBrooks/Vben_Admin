package com.vben.service.security;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * 认证用户主体（由 JWT 解析而来，挂入 ThreadLocal 供业务代码使用）
 */
@Data
@AllArgsConstructor
public class LoginUser {

  private Long userId;
  private String username;

  /** 角色 key 集合（对应前端 roles 数组） */
  private List<String> roles;

  /** 按钮级权限码集合（如 System:User:List / AC_100010） */
  private Set<String> permissions;

  /**
   * token 载荷中的版本号（ver 声明）。历史 token 无该声明时为 0，
   * 由 JwtTokenService 解析时填充；经权限服务重新装配的实体会重置为 0（版本比对只看 token 载荷）。
   */
  private long tokenVersion;

  public boolean hasRole(String role) {
    return roles != null && roles.contains(role);
  }

  public boolean hasPermission(String perm) {
    // 超级角色放行全部权限码（企业惯例）
    return hasRole("super") || (permissions != null && permissions.contains(perm));
  }
}
