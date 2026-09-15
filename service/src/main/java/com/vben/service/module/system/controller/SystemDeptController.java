package com.vben.service.module.system.controller;

import com.vben.service.common.OperLog;
import com.vben.service.common.R;
import com.vben.service.module.system.entity.SysDept;
import com.vben.service.module.system.service.SysDeptAdminService;
import com.vben.service.security.RequirePermission;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 部门管理接口。
 *
 * <p>权限码对齐前端 v-access：
 * <ul>
 *   <li>System:Dept:List   列表/树</li>
 *   <li>System:Dept:Add    新增</li>
 *   <li>System:Dept:Edit   编辑</li>
 *   <li>System:Dept:Delete 删除</li>
 * </ul>
 */
@RestController
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class SystemDeptController {

  private final SysDeptAdminService deptService;

  /** 部门树（嵌套 children，orderNum 升序） */
  @RequirePermission("System:Dept:List")
  @GetMapping("/list")
  public R<List<SysDept>> list() {
    return R.ok(deptService.tree());
  }

  /** 部门详情 */
  @RequirePermission("System:Dept:List")
  @GetMapping("/{id}")
  public R<SysDept> detail(@PathVariable Long id) {
    return R.ok(deptService.detail(id));
  }

  /** 新增部门（parentId=0 为根部门，同级名称唯一） */
  @OperLog(module = "部门管理", description = "新增部门")
  @RequirePermission("System:Dept:Add")
  @PostMapping("/save")
  public R<Void> save(@RequestBody SysDept dept) {
    deptService.saveDept(dept);
    return R.ok();
  }

  /** 编辑部门（防环 + 同级名称唯一） */
  @OperLog(module = "部门管理", description = "编辑部门")
  @RequirePermission("System:Dept:Edit")
  @PutMapping("/update")
  public R<Void> update(@RequestBody SysDept dept) {
    deptService.updateDept(dept);
    return R.ok();
  }

  /** 删除部门（须无子部门且无用户归属） */
  @OperLog(module = "部门管理", description = "删除部门")
  @RequirePermission("System:Dept:Delete")
  @DeleteMapping("/{id}")
  public R<Void> remove(@PathVariable Long id) {
    deptService.remove(id);
    return R.ok();
  }
}
