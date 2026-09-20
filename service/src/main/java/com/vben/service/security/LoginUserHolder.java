package com.vben.service.security;

import com.vben.service.common.BizException;

/**
 * 当前登录用户 ThreadLocal 容器（由 JwtAuthFilter 写入/清理）
 */
public final class LoginUserHolder {

  private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

  private LoginUserHolder() {}

  public static void set(LoginUser user) {
    CONTEXT.set(user);
  }

  public static LoginUser get() {
    return CONTEXT.get();
  }

  /** 未登录时抛 401 */
  public static LoginUser require() {
    LoginUser user = CONTEXT.get();
    if (user == null) {
      throw BizException.unauthorized("error.unauthorized");
    }
    return user;
  }

  public static void clear() {
    CONTEXT.remove();
  }
}
