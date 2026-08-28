package com.vben.service.security;

import com.vben.service.common.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@link RequirePermission} 注解拦截器：校验当前用户是否拥有对应权限码
 */
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
      Object handler) {
    if (!(handler instanceof HandlerMethod method)) {
      return true;
    }
    RequirePermission annotation = method.getMethodAnnotation(RequirePermission.class);
    if (annotation == null) {
      return true;
    }

    LoginUser user = LoginUserHolder.require();
    for (String perm : annotation.value()) {
      if (user.hasPermission(perm)) {
        return true;
      }
    }
    throw BizException.forbidden("Forbidden Exception: 缺少权限码 "
        + String.join(" / ", annotation.value()));
  }
}
