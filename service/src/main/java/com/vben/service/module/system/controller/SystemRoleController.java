package com.vben.service.module.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.vben.service.common.OperLog;
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
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色管理后台接口（系统管理页使用）。
 *
 * <p>权限码：System:Role:List / Add / Edit / Delete / Auth（分配菜单）。
 */
@RestController
@Tag(name = "系统角色", description = "角色管理：增删改查、菜单授权、分配数据权限")
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

  /**
   * 角色下拉选项：登录即可访问（用户表单/菜单表单的授权下拉需要），
   * 只暴露非敏感字段，不要求 System:Role:List。
   */
  @GetMapping("/options")
  public R<List<Map<String, Object>>> options() {
    List<Map<String, Object>> data = roleService.options().stream()
        .map(r -> {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("id", r.getId());
          m.put("roleKey", r.getRoleKey());
          m.put("roleName", r.getRoleName());
          m.put("sortNum", r.getSortNum());
          m.put("status", r.getStatus());
          return m;
        })
        .toList();
    return R.ok(data);
  }

  @RequirePermission("System:Role:List")
  @GetMapping("/{id}")
  public R<SysRole> detail(@PathVariable Long id) {
    return R.ok(roleService.detail(id));
  }

  @OperLog(module = "角色管理", description = "新增角色")
  @RequirePermission("System:Role:Add")
  @PostMapping("/save")
  public R<Long> save(@RequestBody SysRole role) {
    return R.ok(roleService.saveRole(role));
  }

  @OperLog(module = "角色管理", description = "编辑角色")
  @RequirePermission("System:Role:Edit")
  @PutMapping("/update")
  public R<Void> update(@RequestBody SysRole role) {
    roleService.updateRole(role);
    return R.ok();
  }

  @OperLog(module = "角色管理", description = "删除角色")
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
  @OperLog(module = "角色管理", description = "分配菜单")
  @RequirePermission("System:Role:Auth")
  @PostMapping("/assign")
  public R<Void> assign(@RequestBody AssignMenuDto dto) {
    roleService.assignMenus(dto.getRoleId(), dto.getMenuIds());
    return R.ok();
  }

  /** 角色数据范围配置（dataScope + 自定义部门集合，编辑表单回显） */
  @RequirePermission("System:Role:List")
  @GetMapping("/data-scope/{roleId}")
  public R<Map<String, Object>> dataScope(@PathVariable Long roleId) {
    return R.ok(roleService.dataScopeDetail(roleId));
  }

  /** 更新角色数据范围 */
  @OperLog(module = "角色管理", description = "设置数据范围")
  @RequirePermission("System:Role:Auth")
  @PutMapping("/data-scope")
  public R<Void> updateDataScope(@RequestBody DataScopeDto dto) {
    roleService.updateDataScope(dto.getRoleId(), dto.getDataScope(), dto.getDeptIds());
    return R.ok();
  }

  /** 数据范围更新入参 */
  public static class DataScopeDto {
    private Long roleId;
    private String dataScope;
    private List<Long> deptIds;

    public Long getRoleId() {
      return roleId;
    }

    public void setRoleId(Long roleId) {
      this.roleId = roleId;
    }

    public String getDataScope() {
      return dataScope;
    }

    public void setDataScope(String dataScope) {
      this.dataScope = dataScope;
    }

    public List<Long> getDeptIds() {
      return deptIds;
    }

    public void setDeptIds(List<Long> deptIds) {
      this.deptIds = deptIds;
    }
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
