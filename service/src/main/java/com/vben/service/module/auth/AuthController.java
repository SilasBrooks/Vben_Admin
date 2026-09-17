package com.vben.service.module.auth;

import com.vben.service.common.BizException;
import com.vben.service.common.IpUtil;
import com.vben.service.common.R;
import com.vben.service.common.ratelimit.RateLimit;
import com.vben.service.module.auth.dto.LoginRequest;
import com.vben.service.module.auth.dto.LoginResult;
import com.vben.service.module.monitor.service.MonitorLoginLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.module.system.service.SysPermissionService;
import com.vben.service.security.JwtTokenService;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import com.vben.service.security.OnlineSessionService;
import com.vben.service.security.TokenVersionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 认证接口——协议与前端 apps/backend-mock/api/auth 完全对齐
 */
@Slf4j
@Tag(name = "认证授权", description = "登录验证码、登录、Token 刷新、登出、权限码、改密")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final SysUserMapper userMapper;
  private final SysPermissionService permissionService;
  private final JwtTokenService jwtTokenService;
  private final RefreshTokenCookieService cookieService;
  private final MonitorLoginLogService loginLogService;
  private final CaptchaService captchaService;
  private final LoginAttemptService loginAttemptService;
  private final TokenVersionService tokenVersionService;
  private final OnlineSessionService onlineSessionService;

  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  /**
   * 图形验证码：登录弹窗展示用，2 分钟有效、一次性使用（公开接口，无需登录）
   */
  @Operation(summary = "获取登录图形验证码", description = "返回 captchaId 与 base64 PNG；2 分钟有效、一次性使用；dev 环境附带 devCode 明文回显")
  @RateLimit(name = "auth:captcha", limit = 30, windowSeconds = 60)
  @GetMapping("/captcha")
  public R<CaptchaService.CaptchaImage> captcha() {
    return R.ok(captchaService.generate());
  }

  /**
   * 登录：返回 accessToken（响应体）+ refreshToken（httpOnly Cookie）。
   *
   * <p>校验顺序：接口限流（注解）→ 失败锁定 → 验证码 → 账号密码。
   */
  @Operation(summary = "登录", description = "校验顺序：限流 → 失败锁定 → 验证码 → 账号密码；返回 accessToken，refreshToken 写入 httpOnly Cookie")
  @RateLimit(name = "auth:login", limit = 10, windowSeconds = 60)
  @PostMapping("/login")
  public R<LoginResult> login(@Valid @RequestBody LoginRequest body,
      HttpServletRequest request, HttpServletResponse response) {
    String ip = IpUtil.getClientIp(request);
    loginAttemptService.checkLocked(body.getUsername(), ip);
    if (!captchaService.verify(body.getCaptchaId(), body.getCaptchaCode())) {
      // 验证码失败不计入锁定（防手误误锁），由接口限流兜底
      throw BizException.badRequest("验证码错误或已过期");
    }

    SysUser user = permissionService.findActiveUser(body.getUsername());
    if (user == null || !passwordEncoder.matches(body.getPassword(), user.getPassword())) {
      // 与 mock 对齐：用户名或密码错误返回 403
      loginAttemptService.registerFailure(body.getUsername(), ip);
      loginLogService.record(body.getUsername(), false, "用户名或密码错误", request);
      cookieService.clear(response);
      throw BizException.forbidden("Username or password is incorrect.");
    }
    loginAttemptService.onSuccess(body.getUsername(), ip);

    List<String> roles = userMapper.selectRoleKeysByUserId(user.getId());
    loginLogService.record(user.getUsername(), true, "登录成功", request);
    long ver = tokenVersionService.current(user.getId());
    String accessToken =
        jwtTokenService.generateAccessToken(user.getId(), user.getUsername(), roles, ver);
    String refreshToken =
        jwtTokenService.generateRefreshToken(user.getId(), user.getUsername(), roles, ver);
    cookieService.write(response, refreshToken);
    // 登记在线会话（覆盖旧会话 → 同账号单会话语义）
    onlineSessionService.register(user.getId(), user.getUsername(), user.getNickname(), ip, ver);

    return R.ok(LoginResult.builder()
        .id(user.getId())
        .username(user.getUsername())
        .realName(user.getNickname())
        .roles(roles)
        .homePath(user.getHomePath())
        .avatar(user.getAvatar() == null ? null : "/api/file/" + user.getAvatar() + "/content")
        .accessToken(accessToken)
        .build());
  }

  /**
   * 刷新 accessToken。
   *
   * <p>注意：与 mock 一致，响应体是【纯 token 字符串】（JSON string），
   * 不使用 R 包装——前端 doRefreshToken 直接取 resp.data。
   */
  @Operation(summary = "刷新 accessToken", description = "凭 httpOnly Cookie 中的 refreshToken 换新 accessToken；响应体为纯 token 字符串")
  @PostMapping(value = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
  public String refresh(HttpServletRequest request, HttpServletResponse response) {
    String refreshToken = cookieService.read(request);
    if (refreshToken == null || refreshToken.isBlank()) {
      // 与 mock 对齐：refresh 失败返回 403，触发前端重新登录
      cookieService.clear(response);
      throw BizException.forbidden("Forbidden Exception");
    }

    LoginUser payload = jwtTokenService.parseRefreshToken(refreshToken);
    LoginUser user = payload == null ? null : permissionService.loadLoginUser(payload.getUserId());
    if (user == null) {
      cookieService.clear(response);
      throw BizException.forbidden("Forbidden Exception");
    }
    // refresh 同样校验版本号：被改密/禁用/强退用户的 refreshToken 无法换发新 accessToken
    long currentVer;
    try {
      currentVer = tokenVersionService.current(user.getUserId());
    } catch (org.springframework.dao.DataAccessException e) {
      // fail-closed：版本读取失败视为凭证无效
      cookieService.clear(response);
      throw BizException.forbidden("Forbidden Exception");
    }
    if (payload.getTokenVersion() != currentVer) {
      cookieService.clear(response);
      throw BizException.forbidden("Forbidden Exception");
    }

    String newAccessToken =
        jwtTokenService.generateAccessToken(user.getUserId(), user.getUsername(),
            user.getRoles(), currentVer);
    // 续期 refresh cookie
    cookieService.write(response, refreshToken);
    return newAccessToken;
  }

  /**
   * 登出：清除 refreshToken Cookie 并移除在线会话。幂等，未登录也返回成功。
   */
  @Operation(summary = "登出", description = "清除 refreshToken Cookie 并移除在线会话；幂等")
  @PostMapping("/logout")
  public R<String> logout(HttpServletRequest request, HttpServletResponse response) {
    // logout 在白名单中不经过认证过滤器，这里尽力解析 access token 以定位在线会话
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      LoginUser payload = jwtTokenService.parseAccessToken(header.substring(7));
      if (payload != null) {
        onlineSessionService.remove(payload.getUserId());
      }
    }
    cookieService.clear(response);
    return R.ok("");
  }

  /**
   * 当前用户权限码列表（按钮级权限），供前端 v-access / AccessControl 使用
   */
  @Operation(summary = "当前用户权限码", description = "返回登录用户的按钮级权限码列表，供前端 v-access 指令使用")
  @GetMapping("/codes")
  public R<List<String>> codes() {
    LoginUser user = LoginUserHolder.require();
    return R.ok(user.getPermissions() == null
        ? new ArrayList<>()
        : new ArrayList<>(user.getPermissions()));
  }

  /**
   * 修改自己的密码：校验旧密码 → 写入新密码。
   *
   * <p>无需权限码，任何已登录用户均可调用；只能改自己的。
   */
  @Operation(summary = "修改自己的密码", description = "校验旧密码后写入新密码（至少 6 位）；仅能改自己的")
  @PostMapping("/change-password")
  public R<Void> changePassword(@Valid @RequestBody ChangePasswordDto body) {
    LoginUser current = LoginUserHolder.require();
    SysUser user = permissionService.findActiveUserById(current.getUserId());
    if (user == null) {
      throw BizException.unauthorized("用户不存在或已停用");
    }
    if (!passwordEncoder.matches(body.getOldPassword(), user.getPassword())) {
      throw BizException.badRequest("旧密码不正确");
    }
    if (body.getNewPassword() == null || body.getNewPassword().length() < 6) {
      throw BizException.badRequest("新密码至少 6 位");
    }
    SysUser patch = new SysUser();
    patch.setId(user.getId());
    patch.setPassword(passwordEncoder.encode(body.getNewPassword()));
    userMapper.updateById(patch);
    // 改密成功：版本 +1，本人已签发的全部 token 立即失效（需重新登录）
    tokenVersionService.bump(user.getId());
    return R.ok();
  }

  /** 修改密码入参 */
  @lombok.Data
  public static class ChangePasswordDto {
    @jakarta.validation.constraints.NotBlank
    private String oldPassword;

    @jakarta.validation.constraints.NotBlank
    private String newPassword;
  }
}
