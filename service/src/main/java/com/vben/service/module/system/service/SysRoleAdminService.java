package com.vben.service.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vben.service.common.BizException;
import com.vben.service.module.notice.service.NoticeService;
import com.vben.service.module.system.entity.SysRole;
import com.vben.service.module.system.entity.SysRoleDept;
import com.vben.service.module.system.entity.SysRoleMenu;
import com.vben.service.module.system.entity.SysUserRole;
import com.vben.service.module.system.mapper.SysRoleDeptMapper;
import com.vben.service.module.system.mapper.SysRoleMapper;
import com.vben.service.module.system.mapper.SysRoleMenuMapper;
import com.vben.service.module.system.mapper.SysUserRoleMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色后台管理：分页列表、增删改查、以及角色-菜单授权（重建 sys_role_menu）。
 *
 * <p>保护规则：
 * <ul>
 *   <li>super 角色不可删除（系统失能风险）</li>
 *   <li>super 角色的 roleKey 不可修改（hasPermission 中按 "super" 放行的硬编码依赖）</li>
 *   <li>已分配用户的角色不可删除（避免孤儿 user_role）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SysRoleAdminService extends ServiceImpl<SysRoleMapper, SysRole> {

  private static final String SUPER_ROLE_KEY = "super";

  private final SysRoleMenuMapper roleMenuMapper;
  private final SysUserRoleMapper userRoleMapper;
  private final SysRoleDeptMapper roleDeptMapper;
  private final NoticeService noticeService;

  public IPage<SysRole> page(long pageNo, long pageSize, String roleName, Integer status) {
    LambdaQueryWrapper<SysRole> q = new LambdaQueryWrapper<SysRole>()
        .orderByAsc(SysRole::getSortNum);
    if (roleName != null && !roleName.isBlank()) {
      q.like(SysRole::getRoleName, roleName);
    }
    if (status != null) {
      q.eq(SysRole::getStatus, status);
    }
    return page(new Page<>(pageNo, pageSize), q);
  }

  public List<SysRole> options() {
    return list(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getSortNum));
  }

  public SysRole detail(Long id) {
    return getById(id);
  }

  @Transactional
  public Long saveRole(SysRole role) {
    if (role.getSortNum() == null) {
      role.setSortNum(1);
    }
    if (role.getStatus() == null) {
      role.setStatus(0);
    }
    save(role);
    return role.getId();
  }

  @Transactional
  public void updateRole(SysRole role) {
    SysRole exist = getById(role.getId());
    if (exist == null) {
      throw BizException.badRequest("error.role.notFound");
    }
    // super 角色的 roleKey 不可改（其他系统硬编码依赖 "super" 字符串）
    if (SUPER_ROLE_KEY.equals(exist.getRoleKey())
        && role.getRoleKey() != null
        && !SUPER_ROLE_KEY.equals(role.getRoleKey())) {
      throw BizException.badRequest("error.role.superKeyFixed");
    }
    // super 角色不可停用
    if (SUPER_ROLE_KEY.equals(exist.getRoleKey())
        && role.getStatus() != null
        && role.getStatus() == 1) {
      throw BizException.badRequest("error.role.superCannotDisable");
    }
    updateById(role);
  }

  @Transactional
  public void remove(Long id) {
    SysRole exist = getById(id);
    if (exist == null) {
      throw BizException.badRequest("error.role.notFound");
    }
    if (SUPER_ROLE_KEY.equals(exist.getRoleKey())) {
      throw BizException.badRequest("error.role.superCannotDelete");
    }
    long bound = userRoleMapper.selectCount(
        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
    if (bound > 0) {
      throw BizException.badRequest("error.role.inUse");
    }
    roleMenuMapper.delete(
        new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
    removeById(id);
  }

  /** 角色已分配的菜单 id 列表 */
  public List<Long> menuIds(Long roleId) {
    return roleMenuMapper.selectList(
            new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId))
        .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
  }

  /** 角色数据范围配置：dataScope + 自定义部门集合（仅范围 2 非空） */
  public Map<String, Object> dataScopeDetail(Long roleId) {
    SysRole role = getById(roleId);
    if (role == null) {
      throw BizException.badRequest("error.role.notFound");
    }
    List<Long> deptIds = roleDeptMapper.selectList(
            new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, roleId))
        .stream().map(SysRoleDept::getDeptId).collect(Collectors.toList());
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("dataScope", role.getDataScope() == null ? "5" : role.getDataScope());
    result.put("deptIds", deptIds);
    return result;
  }

  /** 更新角色数据范围；范围=2 时保存自定义部门集合，否则清空关联记录 */
  @Transactional
  public void updateDataScope(Long roleId, String dataScope, List<Long> deptIds) {
    SysRole role = getById(roleId);
    if (role == null) {
      throw BizException.badRequest("error.role.notFound");
    }
    if (dataScope == null || !"12345".contains(dataScope) || dataScope.length() != 1) {
      throw BizException.badRequest("error.role.dataScope.invalid");
    }
    if ("super".equals(role.getRoleKey()) && !"1".equals(dataScope)) {
      throw BizException.badRequest("error.role.dataScope.superFixed");
    }
    if ("2".equals(dataScope) && (deptIds == null || deptIds.isEmpty())) {
      throw BizException.badRequest("error.role.dataScope.deptRequired");
    }
    SysRole patch = new SysRole();
    patch.setId(roleId);
    patch.setDataScope(dataScope);
    updateById(patch);
    roleDeptMapper.delete(
        new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, roleId));
    if ("2".equals(dataScope)) {
      for (Long deptId : deptIds) {
        SysRoleDept rd = new SysRoleDept();
        rd.setRoleId(roleId);
        rd.setDeptId(deptId);
        roleDeptMapper.insert(rd);
      }
    }
  }

  /** 重新分配角色菜单（先删后插）；成功后通知该角色下全部用户权限已更新 */
  @Transactional
  public void assignMenus(Long roleId, List<Long> menuIds) {
    SysRole role = getById(roleId);
    if (role == null) {
      throw BizException.badRequest("error.role.notFound");
    }
    roleMenuMapper.delete(
        new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
    for (Long menuId : menuIds) {
      SysRoleMenu rm = new SysRoleMenu();
      rm.setRoleId(roleId);
      rm.setMenuId(menuId);
      roleMenuMapper.insert(rm);
    }
    // 授权成功后通知角色下全部当事人（落库 + WebSocket 推送，推送失败不影响结果）
    for (Long userId : userRoleMapper.selectUserIdsByRoleId(roleId)) {
      noticeService.send(userId, "您的功能权限已更新",
          "管理员调整了角色「" + role.getRoleName() + "」的菜单授权，重新登录或刷新后生效。");
    }
  }
}
