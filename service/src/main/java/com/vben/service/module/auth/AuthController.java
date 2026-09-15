package com.vben.service.module.auth;

import com.vben.service.common.BizException;
import com.vben.service.common.R;
import com.vben.service.module.auth.dto.LoginRequest;
import com.vben.service.module.auth.dto.LoginResult;
import com.vben.service.module.monitor.service.MonitorLoginLogService;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.module.system.service.SysPermissionService;
import com.vben.service.security.JwtTokenService;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
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
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

  private final SysUserMapper userMapper;
  private final SysPermissionService permissionService;
  private final JwtTokenService jwtTokenService;
  private final RefreshTokenCookieService cookieService;
  private final MonitorLoginLogService loginLogService;

  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  /**
   * 登录：返回 accessToken（响应体）+ refreshToken（httpOnly Cookie）
   */
  @PostMapping("/login")
  public R<LoginResult> login(@Valid @RequestBody LoginRequest body,
      HttpServletRequest request, HttpServletResponse response) {
    SysUser user = permissionService.findActiveUser(body.getUsername());
    if (user == null || !passwordEncoder.matches(body.getPassword(), user.getPassword())) {
      // 与 mock 对齐：用户名或密码错误返回 403
      loginLogService.record(body.getUsername(), false, "用户名或密码错误", request);
      cookieService.clear(response);
      throw BizException.forbidden("Username or password is incorrect.");
    }

    List<String> roles = userMapper.selectRoleKeysByUserId(user.getId());
    loginLogService.record(user.getUsername(), true, "登录成功", request);
    String accessToken =
        jwtTokenService.generateAccessToken(user.getId(), user.getUsername(), roles);
    String refreshToken =
        jwtTokenService.generateRefreshToken(user.getId(), user.getUsername(), roles);
    cookieService.write(response, refreshToken);

    return R.ok(LoginResult.builder()
        .id(user.getId())
        .username(user.getUsername())
        .realName(user.getNickname())
        .roles(roles)
        .homePath(user.getHomePath())
        .accessToken(accessToken)
        .build());
  }

  /**
   * 刷新 accessToken。
   *
   * <p>注意：与 mock 一致，响应体是【纯 token 字符串】（JSON string），
   * 不使用 R 包装——前端 doRefreshToken 直接取 resp.data。
   */
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

    String newAccessToken =
        jwtTokenService.generateAccessToken(user.getUserId(), user.getUsername(),
            user.getRoles());
    // 续期 refresh cookie
    cookieService.write(response, refreshToken);
    return newAccessToken;
  }

  /**
   * 登出：清除 refreshToken Cookie。幂等，未登录也返回成功。
   */
  @PostMapping("/logout")
  public R<String> logout(HttpServletRequest request, HttpServletResponse response) {
    cookieService.clear(response);
    return R.ok("");
  }

  /**
   * 当前用户权限码列表（按钮级权限），供前端 v-access / AccessControl 使用
   */
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
