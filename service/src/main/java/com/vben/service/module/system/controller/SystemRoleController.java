package com.vben.service.module.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.vben.service.common.R;
import com.vben.service.module.system.entity.SysRole;
import com.vben.service.module.system.service.SysRoleAdminService;
import com.vben.service.security.RequirePermission;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色管理后台接口（系统管理页使用）。
 *
 * <p>权限码：System:Role:List / Add / Edit / Delete / Auth（分配菜单）。
 */
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SystemRoleController {

  private final SysRoleAdminService roleService;

  @RequirePermission("System:Role:List")
  @GetMapping("/list")
  public R<Map<String, Object>> list(@RequestParam(defaultValue = "1") long pageNo,
      @RequestParam(defaultValue = "10") long pageSize,
      @RequestParam(required = false) String roleName,
      @RequestParam(required = false) Integer status) {
    IPage<SysRole> page = roleService.page(pageNo, pageSize, roleName, status);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("items", page.getRecords());
    data.put("total", page.getTotal());
    return R.ok(data);
  }

  @RequirePermission("System:Role:List")
  @GetMapping("/options")
  public R<List<SysRole>> options() {
    return R.ok(roleService.options());
  }

  @RequirePermission("System:Role:List")
  @GetMapping("/{id}")
  public R<SysRole> detail(@PathVariable Long id) {
    return R.ok(roleService.detail(id));
  }

  @RequirePermission("System:Role:Add")
  @PostMapping("/save")
  public R<Void> save(@RequestBody SysRole role) {
    roleService.saveRole(role);
    return R.ok();
  }

  @RequirePermission("System:Role:Edit")
  @PutMapping("/update")
  public R<Void> update(@RequestBody SysRole role) {
    roleService.updateRole(role);
    return R.ok();
  }

  @RequirePermission("System:Role:Delete")
  @DeleteMapping("/{id}")
  public R<Void> remove(@PathVariable Long id) {
    roleService.remove(id);
    return R.ok();
  }

  /** 角色已分配菜单 id 列表（授权对话框树默认勾选） */
  @RequirePermission("System:Role:Auth")
  @GetMapping("/menu-ids/{roleId}")
  public R<List<Long>> menuIds(@PathVariable Long roleId) {
    return R.ok(roleService.menuIds(roleId));
  }

  /** 分配菜单给角色 */
  @RequirePermission("System:Role:Auth")
  @PostMapping("/assign")
  public R<Void> assign(@RequestBody AssignMenuDto dto) {
    roleService.assignMenus(dto.getRoleId(), dto.getMenuIds());
    return R.ok();
  }

  /** 授权入参 */
  public static class AssignMenuDto {
    private Long roleId;
    private List<Long> menuIds;

    public Long getRoleId() {
      return roleId;
    }

    public void setRoleId(Long roleId) {
      this.roleId = roleId;
    }

    public List<Long> getMenuIds() {
      return menuIds;
    }

    public void setMenuIds(List<Long> menuIds) {
      this.menuIds = menuIds;
    }
  }
}
