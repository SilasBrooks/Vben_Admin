package com.vben.service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.I18nMessage;
import com.vben.service.common.IpUtil;
import com.vben.service.common.R;
import com.vben.service.module.system.service.SysPermissionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * JWT 认证过滤器：校验 Authorization: Bearer xxx
 *
 * <p>白名单（登录、刷新、登出、预检请求）直接放行；
 * 无效 token 统一返回 401 + R 结构，与前端 backend-mock 行为一致，
 * 前端拦截器检测到 401 会自动尝试 refresh。
 * 另校验 token 版本号（ver 声明）与 Redis 当前版本一致，实现改密/禁用/强退后旧 token 即时失效；
 * 版本读取异常时 fail-closed（拒绝请求），避免宕机窗口被利用。
 *
 * <p>定向 Cookie 回退：GET /file/{id}/content（头像 <img src> 直链场景）在无 Authorization 头时，
 * 接受 httpOnly jwt Cookie（refresh token）作为凭据——refresh 同样验签 + 比对版本号，
 * 不是匿名放行；其余请求无 Bearer 头一律 401。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

  private static final AntPathMatcher MATCHER = new AntPathMatcher();

  /** 无需认证的路径（相对 context-path）；/ws/** 的鉴权由 WebSocket 握手拦截器自行完成 */
  private static final List<String> WHITE_LIST =
      List.of("/auth/login", "/auth/captcha", "/auth/refresh", "/auth/logout", "/h2-console/**",
          "/error", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/ws/**");

  /** 允许 Cookie 回退的路径（头像/文件直链，仅 GET） */
  private static final List<String> COOKIE_FALLBACK_PATHS = List.of("/file/*/content");

  private final JwtTokenService jwtTokenService;
  private final TokenVersionService tokenVersionService;
  private final SysPermissionService permissionService;
  private final com.vben.service.module.auth.RefreshTokenCookieService cookieService;
  private final OnlineSessionService onlineSessionService;
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response, @NonNull FilterChain chain)
      throws ServletException, IOException {

    if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isWhiteListed(request)) {
      chain.doFilter(request, response);
      return;
    }

    String header = request.getHeader("Authorization");
    String token = null;
    boolean refreshCookieFallback = false;
    if (header != null && header.startsWith("Bearer ")) {
      token = header.substring(7);
    } else if (isCookieFallbackAllowed(request)) {
      // <img src> 无法携带 Authorization 头，用 httpOnly refresh Cookie 作为凭据
      token = cookieService.read(request);
      refreshCookieFallback = token != null && !token.isBlank();
    }
    if (token == null || token.isBlank()) {
      writeUnauthorized(request, response);
      return;
    }

    LoginUser payload = refreshCookieFallback
        ? jwtTokenService.parseRefreshToken(token)
        : jwtTokenService.parseAccessToken(token);
    if (payload == null) {
      writeUnauthorized(request, response);
      return;
    }

    // 版本号比对：改密/重置密码/禁用/强退后 bump，旧 token 在此被拒。
    // Redis 不可用时拒绝请求（fail-closed），不放行无版本校验的流量
    try {
      if (payload.getTokenVersion() != tokenVersionService.current(payload.getUserId())) {
        writeUnauthorized(request, response);
        return;
      }
    } catch (DataAccessException e) {
      writeUnauthorized(request, response);
      return;
    }

    // 以库中最新角色/权限为准（token 中的 roles 仅作冗余）
    LoginUser user = permissionService.loadLoginUser(payload.getUserId());
    if (user == null) {
      writeUnauthorized(request, response);
      return;
    }

    // 认证成功：续期在线活动窗口（滑动过期，窗口内无请求即视为离线；失败不影响请求）
    onlineSessionService.touchOrRegister(payload.getUserId(), payload.getUsername(),
        IpUtil.getClientIp(request), payload.getTokenVersion());

    try {
      LoginUserHolder.set(user);
      chain.doFilter(request, response);
    } finally {
      LoginUserHolder.clear();
    }
  }

  private boolean isWhiteListed(HttpServletRequest request) {
    String path = request.getRequestURI()
        .substring(request.getContextPath().length());
    return WHITE_LIST.stream().anyMatch(p -> MATCHER.match(p, path));
  }

  private boolean isCookieFallbackAllowed(HttpServletRequest request) {
    if (!"GET".equalsIgnoreCase(request.getMethod())) {
      return false;
    }
    String path = request.getRequestURI()
        .substring(request.getContextPath().length());
    return COOKIE_FALLBACK_PATHS.stream().anyMatch(p -> MATCHER.match(p, path));
  }

  private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    // Filter 早于 DispatcherServlet 执行，LocaleContextHolder 未生效：按请求头手动解析语言
    Locale locale = request.getLocale() != null ? request.getLocale() : Locale.SIMPLIFIED_CHINESE;
    String msg = I18nMessage.get(locale, "error.unauthorized");
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(objectMapper.writeValueAsString(R.fail(msg, msg)));
  }
}
