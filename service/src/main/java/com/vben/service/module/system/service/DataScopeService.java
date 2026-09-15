package com.vben.service.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vben.service.common.DataScopeHolder;
import com.vben.service.module.system.entity.SysDept;
import com.vben.service.module.system.entity.SysRole;
import com.vben.service.module.system.entity.SysRoleDept;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysDeptMapper;
import com.vben.service.module.system.mapper.SysRoleDeptMapper;
import com.vben.service.module.system.mapper.SysRoleMapper;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 数据范围解析：把当前用户的角色 data_scope 配置解析为最终可见边界。
 *
 * <p>规则（见 change add-data-permission-scope specs）：
 * <ul>
 *   <li>超管（super 角色）恒为全部数据</li>
 *   <li>任一角色 dataScope=1 → 全部数据</li>
 *   <li>范围 3=本部门、4=本部门及以下、2=自定义部门（选父含子），多角色取并集</li>
 *   <li>部门类范围恒包含未归属数据（dept_id IS NULL）与本人，防数据"消失"</li>
 *   <li>仅存在范围 5（或无任何范围配置）→ 仅本人</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

  private final SysUserMapper userMapper;
  private final SysRoleMapper roleMapper;
  private final SysRoleDeptMapper roleDeptMapper;
  private final SysDeptMapper deptMapper;

  /** 解析当前登录用户的数据范围并放入 Holder */
  public void applyForCurrentUser() {
    LoginUser user = LoginUserHolder.get();
    if (user == null) {
      return;
    }
    DataScopeHolder.set(resolve(user));
  }

  /** 解析用户可见边界（公开方法，便于单元验证与后续模块复用） */
  public DataScopeHolder.DataScopeInfo resolve(LoginUser user) {
    // 超管恒为全部
    if (user.hasRole("super")) {
      return new DataScopeHolder.DataScopeInfo(
          DataScopeHolder.Type.ALL, Set.of(), false, user.getUserId());
    }

    List<Long> roleIds = userMapper.selectRoleIdsByUserId(user.getUserId());
    List<SysRole> roles = roleIds.isEmpty()
        ? List.of()
        : roleMapper.selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds));

    Set<Long> deptIds = new HashSet<>();
    boolean hasDeptScope = false;
    boolean onlySelf = roles.isEmpty();
    for (SysRole role : roles) {
      String scope = role.getDataScope() == null ? "5" : role.getDataScope();
      switch (scope) {
        case "1" -> {
          // 任一角色全部数据 → 直接放行
          return new DataScopeHolder.DataScopeInfo(
              DataScopeHolder.Type.ALL, Set.of(), false, user.getUserId());
        }
        case "2" -> {
          hasDeptScope = true;
          deptIds.addAll(customDeptIds(role.getId()));
        }
        case "3" -> {
          hasDeptScope = true;
          Long ownDept = ownDeptId(user.getUserId());
          if (ownDept != null) {
            deptIds.add(ownDept);
          }
        }
        case "4" -> {
          hasDeptScope = true;
          Long ownDept = ownDeptId(user.getUserId());
          if (ownDept != null) {
            deptIds.add(ownDept);
            deptIds.addAll(descendantIds(ownDept));
          }
        }
        default -> {
          // 5 仅本人，计入 onlySelf 兜底
        }
      }
    }

    if (!hasDeptScope) {
      return new DataScopeHolder.DataScopeInfo(
          DataScopeHolder.Type.SELF, Set.of(), false, user.getUserId());
    }
    return new DataScopeHolder.DataScopeInfo(
        DataScopeHolder.Type.DEPT, deptIds, true, user.getUserId());
  }

  /** 自定义部门集合（含子孙部门展开） */
  private Set<Long> customDeptIds(Long roleId) {
    List<Long> configured = roleDeptMapper.selectList(
            new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, roleId))
        .stream().map(SysRoleDept::getDeptId).toList();
    Set<Long> result = new HashSet<>(configured);
    for (Long deptId : configured) {
      result.addAll(descendantIds(deptId));
    }
    return result;
  }

  /** 用户归属部门 id（未归属返回 null） */
  private Long ownDeptId(Long userId) {
    SysUser u = userMapper.selectById(userId);
    return u == null ? null : u.getDeptId();
  }

  /** 递归收集某部门的全部子孙部门 id */
  private Set<Long> descendantIds(Long deptId) {
    List<SysDept> all = deptMapper.selectList(null);
    Map<Long, List<Long>> childrenMap = new HashMap<>();
    for (SysDept d : all) {
      childrenMap.computeIfAbsent(d.getParentId(), k -> new java.util.ArrayList<>()).add(d.getId());
    }
    Set<Long> result = new HashSet<>();
    collect(deptId, childrenMap, result);
    return result;
  }

  private void collect(Long parent, Map<Long, List<Long>> childrenMap, Set<Long> out) {
    List<Long> children = childrenMap.get(parent);
    if (children == null) {
      return;
    }
    for (Long child : children) {
      if (out.add(child)) {
        collect(child, childrenMap, out);
      }
    }
  }
}
