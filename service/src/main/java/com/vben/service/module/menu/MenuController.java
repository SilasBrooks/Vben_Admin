package com.vben.service.module.menu;

import com.vben.service.common.R;
import com.vben.service.security.LoginUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 动态路由菜单接口——对齐前端 mock /menu/all（backend 菜单模式）
 */
@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class MenuController {

  private final SysMenuService menuService;

  @GetMapping("/all")
  public R<List<VbenRoute>> all() {
    return R.ok(menuService.buildRouteTree(LoginUserHolder.require().getUserId()));
  }
}
