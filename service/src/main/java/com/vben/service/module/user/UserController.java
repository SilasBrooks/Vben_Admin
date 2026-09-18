package com.vben.service.module.user;

import com.vben.service.common.R;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.module.system.service.SysPermissionService;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当前用户信息与本人资料接口——对齐前端 mock /user/info
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

  private final SysPermissionService permissionService;
  private final SysUserMapper userMapper;

  /**
   * 返回结构：{ id, username, realName, roles, homePath, avatar }，与 mock 的 UserInfo 一致
   */
  @GetMapping("/info")
  public R<Map<String, Object>> info() {
    LoginUser login = LoginUserHolder.require();
    SysUser user = permissionService.findActiveUserById(login.getUserId());
    if (user == null) {
      throw com.vben.service.common.BizException.unauthorized("Unauthorized Exception");
    }

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", user.getId());
    data.put("username", user.getUsername());
    data.put("realName", user.getNickname());
    data.put("roles", login.getRoles());
    if (user.getHomePath() != null) {
      data.put("homePath", user.getHomePath());
    }
    if (user.getAvatar() != null) {
      data.put("avatar", "/api/file/" + user.getAvatar() + "/content");
    }
    data.put("introduction", user.getIntroduction() == null ? "" : user.getIntroduction());
    data.put("email", user.getEmail() == null ? "" : user.getEmail());
    return R.ok(data);
  }

  /**
   * 修改本人资料：仅允许改 nickname / introduction / email。
   *
   * <p>无需权限码，任何已登录用户均可调用；只能改自己的。username、password、avatar
   * 不在此接口范围内（头像走 POST /file/avatar，改密走 POST /auth/change-password）。
   * 成功后返回最新的本人信息，便于前端直接刷新 store。
   */
  @PatchMapping("/profile")
  public R<Map<String, Object>> profile(@Valid @RequestBody UpdateProfileDto body) {
    LoginUser login = LoginUserHolder.require();
    SysUser user = permissionService.findActiveUserById(login.getUserId());
    if (user == null) {
      throw com.vben.service.common.BizException.unauthorized("Unauthorized Exception");
    }
    SysUser patch = new SysUser();
    patch.setId(user.getId());
    patch.setNickname(body.getNickname());
    patch.setIntroduction(body.getIntroduction());
    // 空串归一为 NULL，避免下拉/详情显示占位空白
    patch.setEmail(body.getEmail() == null || body.getEmail().isBlank() ? null : body.getEmail().trim());
    userMapper.updateById(patch);
    return info();
  }

  /** 修改本人资料入参 */
  @lombok.Data
  public static class UpdateProfileDto {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 200)
    private String nickname;

    @jakarta.validation.constraints.Size(max = 200)
    private String introduction;

    @jakarta.validation.constraints.Email(message = "邮箱格式不正确")
    @jakarta.validation.constraints.Size(max = 255)
    private String email;
  }
}
