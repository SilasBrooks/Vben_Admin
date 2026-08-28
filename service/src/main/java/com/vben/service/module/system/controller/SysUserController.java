package com.vben.service.module.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.vben.service.common.R;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.service.SysUserAdminService;
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
 * 用户管理接口（RBAC 标准实现：分页 + CRUD + 分配角色 + 重置密码）。
 *
 * <p>权限码：
 * <ul>
 *   <li>System:User:List      列表/详情/查角色 id</li>
 *   <li>System:User:Add      新增</li>
 *   <li>System:User:Edit     编辑/分配角色</li>
 *   <li>System:User:Delete    删除</li>
 *   <li>System:User:ResetPwd 重置密码</li>
 * </ul>
 *
 * <p>返回结构统一 {items, total}，可直接对接 vben vxe-table。
 * password 字段在所有出参中已清空。
 */
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController {

  private final SysUserAdminService userService;

  /** 用户分页列表（支持按用户名模糊 + 状态过滤） */
  @RequirePermission("System:User:List")
  @GetMapping("/list")
  public R<Map<String, Object>> list(
      @RequestParam(defaultValue = "1") long pageNo,
      @RequestParam(defaultValue = "10") long pageSize,
      @RequestParam(required = false) String username,
      @RequestParam(required = false) Integer status) {

    IPage<SysUser> page = userService.page(pageNo, pageSize, username, status);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("items", page.getRecords());
    data.put("total", page.getTotal());
    return R.ok(data);
  }

  /** 用户详情（含已分配角色 id 列表，密码已清空） */
  @RequirePermission("System:User:List")
  @GetMapping("/{id}")
  public R<SysUser> detail(@PathVariable Long id) {
    return R.ok(userService.detail(id));
  }

  /** 用户已分配的角色 id 列表（编辑回显） */
  @RequirePermission("System:User:List")
  @GetMapping("/{id}/role-ids")
  public R<List<Long>> roleIds(@PathVariable Long id) {
    return R.ok(userService.roleIds(id));
  }

  /** 新增用户（username + password 必填，roleIds 可选） */
  @RequirePermission("System:User:Add")
  @PostMapping("/save")
  public R<Void> save(@RequestBody SysUser user) {
    userService.saveUser(user);
    return R.ok();
  }

  /** 编辑用户（username 不可改，password 走 reset-password，roleIds 可选） */
  @RequirePermission("System:User:Edit")
  @PutMapping("/update")
  public R<Void> update(@RequestBody SysUser user) {
    userService.updateUser(user);
    return R.ok();
  }

  /** 删除用户（禁止自删、禁止删除最后一个 super） */
  @RequirePermission("System:User:Delete")
  @DeleteMapping("/{id}")
  public R<Void> remove(@PathVariable Long id) {
    userService.remove(id);
    return R.ok();
  }

  /** 重置密码（body: {newPassword}，至少 6 位） */
  @RequirePermission("System:User:ResetPwd")
  @PostMapping("/{id}/reset-password")
  public R<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordDto dto) {
    userService.resetPassword(id, dto.getNewPassword());
    return R.ok();
  }

  /** 分配角色（body: {roleIds}，全量替换） */
  @RequirePermission("System:User:Edit")
  @PostMapping("/{id}/assign-roles")
  public R<Void> assignRoles(@PathVariable Long id, @RequestBody AssignRolesDto dto) {
    userService.assignRoles(id, dto.getRoleIds());
    return R.ok();
  }

  /** 重置密码入参 */
  public static class ResetPasswordDto {
    private String newPassword;

    public String getNewPassword() {
      return newPassword;
    }

    public void setNewPassword(String newPassword) {
      this.newPassword = newPassword;
    }
  }

  /** 分配角色入参 */
  public static class AssignRolesDto {
    private List<Long> roleIds;

    public List<Long> getRoleIds() {
      return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
      this.roleIds = roleIds;
    }
  }
}
