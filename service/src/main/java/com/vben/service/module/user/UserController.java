package com.vben.service.module.user;

import com.vben.service.common.R;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.service.SysPermissionService;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当前用户信息接口——对齐前端 mock /user/info
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

  private final SysPermissionService permissionService;

  /**
   * 返回结构：{ id, username, realName, roles, homePath }，与 mock 的 UserInfo 一致
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
    return R.ok(data);
  }
}
