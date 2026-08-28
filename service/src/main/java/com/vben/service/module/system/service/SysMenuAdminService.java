package com.vben.service.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vben.service.common.BizException;
import com.vben.service.module.system.entity.SysMenu;
import com.vben.service.module.system.entity.SysRole;
import com.vben.service.module.system.entity.SysRoleMenu;
import com.vben.service.module.system.mapper.SysMenuMapper;
import com.vben.service.module.system.mapper.SysRoleMapper;
import com.vben.service.module.system.mapper.SysRoleMenuMapper;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 菜单后台管理（系统管理页 CRUD，区别于 module.menu 下的动态路由转换服务）。
 *
 * <p>菜单树以 parentId 组织，排序按 orderNum 升序。删除菜单前会校验是否存在子节点，
 * 并清理 sys_role_menu 关联。
 *
 * <p>自动授权规则（save/update 时触发）：
 * <ul>
 *   <li>authority 显式指定角色 → 按其逗号分隔列表授权</li>
 *   <li>authority 为空 → 默认授权给「当前登录用户的所有角色 + super」，
 *       保证创建者本人立即可见</li>
 *   <li>M / C / F 三种类型都会被授权（F 权限码也需要角色持有才能让按钮可用）</li>
 *   <li>updateMenu 时按 authority diff 增删授权，未在 authority 中的「手动授权」保留</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SysMenuAdminService extends ServiceImpl<SysMenuMapper, SysMenu> {

  private static final String SUPER_ROLE_KEY = "super";

  private final SysRoleMenuMapper roleMenuMapper;
  private final SysRoleMapper roleMapper;

  /** 菜单树（按 parentId 聚合成 children） */
  public List<SysMenu> tree() {
    List<SysMenu> all = list(new LambdaQueryWrapper<SysMenu>()
        .orderByAsc(SysMenu::getParentId)
        .orderByAsc(SysMenu::getOrderNum));
    return buildTree(all, 0L);
  }

  public SysMenu detail(Long id) {
    return getById(id);
  }

  @Transactional
  public void saveMenu(SysMenu menu) {
    if (menu.getParentId() == null) {
      menu.setParentId(0L);
    }
    if (menu.getOrderNum() == null) {
      menu.setOrderNum(1);
    }
    fillDefault(menu);
    save(menu);
    // 新增菜单：按 authority 自动授权（authority 为空时给当前用户角色 + super）
    Set<String> targetRoles = resolveTargetRoles(menu);
    grantToRoles(menu, targetRoles);
  }

  @Transactional
  public void updateMenu(SysMenu menu) {
    SysMenu exist = getById(menu.getId());
    if (exist == null) {
      throw BizException.badRequest("菜单不存在");
    }
    if (Objects.equals(exist.getParentId(), menu.getId())) {
      throw BizException.badRequest("父级菜单不能选择自身");
    }
    fillDefault(menu);
    updateById(menu);
    // 编辑：按 authority diff 增删授权，保留 authority 之外的手动授权
    syncGrantsOnUpdate(exist, menu);
  }

  @Transactional
  public void remove(Long id) {
    long children = count(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
    if (children > 0) {
      throw BizException.badRequest("存在子菜单，无法删除");
    }
    roleMenuMapper.delete(
        new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
    removeById(id);
  }

  // ------------------------------------------------------------------
  // 自动授权内部实现
  // ------------------------------------------------------------------

  /**
   * 解析菜单目标授权角色集合：
   * <ul>
   *   <li>authority 非空 → 解析其逗号分隔的角色 key</li>
   *   <li>authority 为空 → 当前登录用户的所有角色 + super</li>
   * </ul>
   */
  private Set<String> resolveTargetRoles(SysMenu menu) {
    Set<String> roles = parseAuthority(menu.getAuthority());
    if (!roles.isEmpty()) {
      return roles;
    }
    // authority 为空：默认给当前用户角色 + super，保证创建者本人立即可见
    Set<String> fallback = new HashSet<>();
    LoginUser current = LoginUserHolder.get();
    if (current != null && current.getRoles() != null) {
      fallback.addAll(current.getRoles());
    }
    fallback.add(SUPER_ROLE_KEY);
    return fallback;
  }

  /** 给菜单按指定角色集合授权（已存在的不重复插入） */
  private void grantToRoles(SysMenu menu, Set<String> roleKeys) {
    if (roleKeys.isEmpty()) {
      return;
    }
    List<SysRole> roles = roleMapper.selectList(
        new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleKey, roleKeys));
    for (SysRole role : roles) {
      long exist = roleMenuMapper.selectCount(new LambdaQueryWrapper<SysRoleMenu>()
          .eq(SysRoleMenu::getRoleId, role.getId())
          .eq(SysRoleMenu::getMenuId, menu.getId()));
      if (exist == 0) {
        SysRoleMenu rm = new SysRoleMenu();
        rm.setRoleId(role.getId());
        rm.setMenuId(menu.getId());
        roleMenuMapper.insert(rm);
      }
    }
  }

  /**
   * 编辑菜单时同步授权：
   * <ul>
   *   <li>旧 authority 中但不在新 authority 中的角色 → 删除其 role_menu</li>
   *   <li>新 authority 中但角色还未授权的 → 插入</li>
   *   <li>未在 authority 中出现过的「手动授权」角色 → 保留不动</li>
   * </ul>
   */
  private void syncGrantsOnUpdate(SysMenu oldMenu, SysMenu newMenu) {
    Set<String> oldRoles = parseAuthority(oldMenu.getAuthority());
    Set<String> newRoles = resolveTargetRoles(newMenu);

    // authority 没显式变化且新 authority 也非空 → 直接补授权（幂等），跳过删除
    // authority 仍为空时 resolveTargetRoles 给了 fallback，不删除任何东西
    Set<String> toRemove = new HashSet<>(oldRoles);
    toRemove.removeAll(newRoles);

    if (!toRemove.isEmpty()) {
      List<SysRole> rolesToRemove = roleMapper.selectList(
          new LambdaQueryWrapper<SysRole>().in(SysRole::getRoleKey, toRemove));
      for (SysRole role : rolesToRemove) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
            .eq(SysRoleMenu::getRoleId, role.getId())
            .eq(SysRoleMenu::getMenuId, newMenu.getId()));
      }
    }
    grantToRoles(newMenu, newRoles);
  }

  /** 解析 authority 字段为角色 key 集合，空字符串返回空集合 */
  private Set<String> parseAuthority(String authority) {
    if (authority == null || authority.isBlank()) {
      return new HashSet<>();
    }
    Set<String> roles = new HashSet<>();
    for (String k : authority.split(",")) {
      String key = k.trim();
      if (!key.isEmpty()) {
        roles.add(key);
      }
    }
    return roles;
  }

  private void fillDefault(SysMenu menu) {
    if (menu.getVisible() == null) {
      menu.setVisible(0);
    }
    if (menu.getStatus() == null) {
      menu.setStatus(0);
    }
    if (menu.getKeepAlive() == null) {
      menu.setKeepAlive(0);
    }
    if (menu.getAffixTab() == null) {
      menu.setAffixTab(0);
    }
  }

  /** 根据父 id 递归构建树 */
  private List<SysMenu> buildTree(List<SysMenu> all, Long parentId) {
    return all.stream()
        .filter(m -> Objects.equals(m.getParentId(), parentId))
        .sorted(Comparator.comparing(SysMenu::getOrderNum,
            Comparator.nullsLast(Integer::compareTo)))
        .peek(m -> m.setChildren(buildTree(all, m.getId())))
        .collect(Collectors.toList());
  }
}
