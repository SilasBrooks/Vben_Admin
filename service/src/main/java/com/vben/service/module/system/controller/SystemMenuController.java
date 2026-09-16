package com.vben.service.module.system.controller;

import com.vben.service.common.OperLog;
import com.vben.service.common.R;
import com.vben.service.module.system.entity.SysMenu;
import com.vben.service.module.system.service.SysMenuAdminService;
import com.vben.service.security.RequirePermission;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 菜单管理后台接口（系统管理页使用）。
 *
 * <p>权限码对齐前端 v-access：
 * <ul>
 *   <li>System:Menu:List  列表/树</li>
 *   <li>System:Menu:Add   新增</li>
 *   <li>System:Menu:Edit  编辑</li>
 *   <li>System:Menu:Delete 删除</li>
 * </ul>
 */
@RestController
@Tag(name = "系统菜单", description = "菜单/按钮管理：树查询、增删改查")
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class SystemMenuController {

  private final SysMenuAdminService menuService;

  @RequirePermission("System:Menu:List")
  @GetMapping("/list")
  public R<List<SysMenu>> list() {
    return R.ok(menuService.tree());
  }

  @RequirePermission("System:Menu:List")
  @GetMapping("/{id}")
  public R<SysMenu> detail(@PathVariable Long id) {
    return R.ok(menuService.detail(id));
  }

  @OperLog(module = "菜单管理", description = "新增菜单")
  @RequirePermission("System:Menu:Add")
  @PostMapping("/save")
  public R<Void> save(@RequestBody SysMenu menu) {
    menuService.saveMenu(menu);
    return R.ok();
  }

  @OperLog(module = "菜单管理", description = "编辑菜单")
  @RequirePermission("System:Menu:Edit")
  @PutMapping("/update")
  public R<Void> update(@RequestBody SysMenu menu) {
    menuService.updateMenu(menu);
    return R.ok();
  }

  @OperLog(module = "菜单管理", description = "删除菜单")
  @RequirePermission("System:Menu:Delete")
  @DeleteMapping("/{id}")
  public R<Void> remove(@PathVariable Long id) {
    menuService.remove(id);
    return R.ok();
  }
}
