package com.vben.service.bootstrap;

import com.vben.service.module.system.entity.SysMenu;
import com.vben.service.module.system.entity.SysRole;
import com.vben.service.module.system.entity.SysRoleMenu;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.entity.SysUserRole;
import com.vben.service.module.system.mapper.SysMenuMapper;
import com.vben.service.module.system.mapper.SysRoleMapper;
import com.vben.service.module.system.mapper.SysRoleMenuMapper;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.module.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 初始数据装配：内置账号/角色/菜单（与前端 backend-mock 的 MOCK_USERS、
 * MOCK_MENUS、MOCK_CODES 完全对齐，保证替换 mock 后前端行为不变）。
 *
 * <p>仅当 sys_user 表为空时执行，重复启动不会重复插入。
 * 生产环境请登录后立即修改默认密码，或按需调整本类。
 *
 * <p>内置账号（密码均为 123456）：
 * <ul>
 *   <li>vben  / 123456 —— super 超级角色，全部菜单 + 全部权限码</li>
 *   <li>admin / 123456 —— admin 管理角色，权限码 AC_100010/20/30 + System:User:List</li>
 *   <li>jack  / 123456 —— user 普通角色，权限码 AC_1000001 / AC_1000002</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements ApplicationRunner {

  private final SysUserMapper userMapper;
  private final SysRoleMapper roleMapper;
  private final SysMenuMapper menuMapper;
  private final SysUserRoleMapper userRoleMapper;
  private final SysRoleMenuMapper roleMenuMapper;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (userMapper.selectCount(null) > 0) {
      return;
    }
    log.info("检测到空库，开始初始化种子数据（3 账号 / 3 角色 / 菜单与权限码）...");
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // ------------------------------------------------------------------
    // 1. 角色
    // ------------------------------------------------------------------
    Long superRoleId = insertRole("super", "超级管理员", 1);
    Long adminRoleId = insertRole("admin", "管理员", 2);
    Long userRoleId = insertRole("user", "普通用户", 3);

    // ------------------------------------------------------------------
    // 2. 菜单（结构对齐前端 mock：Dashboard + Demos）
    // ------------------------------------------------------------------
    // Dashboard 目录
    Long dashboardId = insertMenu(catalog("Dashboard", "page.dashboard.title", null, -1,
        "/dashboard", "/analytics", 0L, false, false));
    insertMenu(leaf("Analytics", "page.dashboard.analytics", null, 1, "/analytics",
        "/dashboard/analytics/index", dashboardId, false, true));
    insertMenu(leaf("Workspace", "page.dashboard.workspace", null, 2, "/workspace",
        "/dashboard/workspace/index", dashboardId, false, false));

    // 系统管理模块菜单（供前端系统管理页展示，component 对应 views/system/*）
    Long systemCatalogId = insertMenu(catalog("System", "系统管理",
        "ic:baseline-settings", 9000, "/system", "/system/user", 0L, false, false));
    Long systemUserId = insertMenu(leaf("SystemUser", "用户管理",
        "ant-design:user-outlined", 1, "/system/user", "/system/user/index", systemCatalogId,
        false, false).authority("super,admin"));
    Long systemRoleId = insertMenu(leaf("SystemRole", "角色管理",
        "ant-design:user-switch-outlined", 2, "/system/role", "/system/role/index",
        systemCatalogId, false, false).authority("super,admin"));
    Long systemMenuId = insertMenu(leaf("SystemMenu", "菜单管理",
        "ant-design:menu-outlined", 3, "/system/menu", "/system/menu/index", systemCatalogId,
        false, false).authority("super,admin"));
    // 后端接口权限码示例（F 型挂在对应菜单下，不进路由树）
    Long systemUserListId = insertMenu(perm("System:User:List", systemUserId));
    Long userAdd = insertMenu(perm("System:User:Add", systemUserId));
    Long userEdit = insertMenu(perm("System:User:Edit", systemUserId));
    Long userDelete = insertMenu(perm("System:User:Delete", systemUserId));
    Long userResetPwd = insertMenu(perm("System:User:ResetPwd", systemUserId));
    // 角色管理按钮权限码（F 型，挂在角色管理菜单下，不进路由树）
    Long roleAdd = insertMenu(perm("System:Role:Add", systemRoleId));
    Long roleEdit = insertMenu(perm("System:Role:Edit", systemRoleId));
    Long roleDelete = insertMenu(perm("System:Role:Delete", systemRoleId));
    Long roleAuth = insertMenu(perm("System:Role:Auth", systemRoleId));
    // 菜单管理按钮权限码（F 型，挂在菜单管理菜单下，不进路由树）
    Long menuAdd = insertMenu(perm("System:Menu:Add", systemMenuId));
    Long menuEdit = insertMenu(perm("System:Menu:Edit", systemMenuId));
    Long menuDelete = insertMenu(perm("System:Menu:Delete", systemMenuId));

    // 库存管理模块（WSM）：super/admin 可见，user 不可见
    Long wsmCatalogId = insertMenu(catalog("Wsm", "库存管理",
        "ic:baseline-inventory-2", 8000, "/wsm", "/wsm/store", 0L, false, false));
    Long wsmStoreId = insertMenu(leaf("WsmStore", "库存",
        "ic:baseline-inventory", 1, "/wsm/store", "/wsm/store/index", wsmCatalogId,
        false, false).authority("super,admin"));

    // system 资源（含库存模块）对 super/admin 可见，user 不可见
    java.util.Set<Long> systemSet = java.util.Set.of(systemCatalogId, systemUserId,
        systemRoleId, systemMenuId, systemUserListId, userAdd, userEdit, userDelete, userResetPwd,
        roleAdd, roleEdit, roleDelete, roleAuth,
        menuAdd, menuEdit, menuDelete, wsmCatalogId, wsmStoreId);

    // ------------------------------------------------------------------
    // 3. 用户（密码 123456）
    // ------------------------------------------------------------------
    Long vbenId = insertUser("vben", encoder.encode("123456"), "Vben", null);
    Long adminId = insertUser("admin", encoder.encode("123456"), "Admin", "/workspace");
    Long jackId = insertUser("jack", encoder.encode("123456"), "Jack", "/analytics");
    insertUserRole(vbenId, superRoleId);
    insertUserRole(adminId, adminRoleId);
    insertUserRole(jackId, userRoleId);

    // ------------------------------------------------------------------
    // 4. 角色-菜单授权
    //   super：全部菜单与权限
    //   admin：全部（含 system 与 wsm，因 authority 含 admin）
    //   user ：仅公开资源（Dashboard + 不含 systemSet / wsm）
    // ------------------------------------------------------------------
    for (SysMenu m : menuMapper.selectList(null)) {
      Long id = m.getId();
      // super：全部
      insertRoleMenu(superRoleId, id);
      // admin：全部
      insertRoleMenu(adminRoleId, id);
      // user：仅非 systemSet 内资源
      if (!systemSet.contains(id)) {
        insertRoleMenu(userRoleId, id);
      }
    }

    log.info("种子数据初始化完成。账号：vben/123456(super)、admin/123456(admin)、jack/123456(user)");
  }

  // ------------------------------------------------------------------
  // 菜单构造 helpers（链式补充特殊字段）
  // ------------------------------------------------------------------

  private record MenuSpec(SysMenu menu) {
    MenuSpec authority(String roles) {
      menu().setAuthority(roles);
      return this;
    }

    MenuSpec extraMeta(String json) {
      menu().setExtraMeta(json);
      return this;
    }
  }

  /** 目录（M）：无 component，靠 children 渲染 */
  private MenuSpec catalog(String name, String title, String icon, int order, String path,
      String redirect, Long parentId, boolean keepAlive, boolean affixTab) {
    SysMenu m = base(name, "M", title, icon, order, parentId);
    m.setPath(path);
    m.setRedirect(redirect);
    m.setKeepAlive(keepAlive ? 1 : 0);
    m.setAffixTab(affixTab ? 1 : 0);
    return new MenuSpec(m);
  }

  /** 菜单（C）：对应前端一个页面 */
  private MenuSpec leaf(String name, String title, String icon, int order, String path,
      String component, Long parentId, boolean keepAlive, boolean affixTab) {
    SysMenu m = base(name, "C", title, icon, order, parentId);
    m.setPath(path);
    m.setComponent(component);
    m.setKeepAlive(keepAlive ? 1 : 0);
    m.setAffixTab(affixTab ? 1 : 0);
    return new MenuSpec(m);
  }

  /** 按钮权限码（F）：不进路由树 */
  private MenuSpec perm(String code, Long parentId) {
    SysMenu m = base("Perm_" + code.replace(':', '_'), "F", code, null, 99, parentId);
    m.setPerm(code);
    return new MenuSpec(m);
  }

  private SysMenu base(String name, String type, String title, String icon, int order,
      Long parentId) {
    SysMenu m = new SysMenu();
    m.setMenuName(name);
    m.setMenuType(type);
    m.setTitle(title);
    m.setIcon(icon);
    m.setOrderNum(order);
    m.setParentId(parentId);
    m.setVisible(0);
    m.setKeepAlive(0);
    m.setAffixTab(0);
    m.setStatus(0);
    return m;
  }

  private Long insertMenu(MenuSpec spec) {
    menuMapper.insert(spec.menu());
    return spec.menu().getId();
  }

  // ------------------------------------------------------------------
  // 其他 helpers
  // ------------------------------------------------------------------

  private Long insertRole(String key, String name, int sort) {
    SysRole r = new SysRole();
    r.setRoleKey(key);
    r.setRoleName(name);
    r.setSortNum(sort);
    r.setStatus(0);
    roleMapper.insert(r);
    return r.getId();
  }

  private Long insertUser(String username, String passwordHash, String nickname,
      String homePath) {
    SysUser u = new SysUser();
    u.setUsername(username);
    u.setPassword(passwordHash);
    u.setNickname(nickname);
    u.setHomePath(homePath);
    u.setStatus(0);
    userMapper.insert(u);
    return u.getId();
  }

  private void insertUserRole(Long userId, Long roleId) {
    SysUserRole ur = new SysUserRole();
    ur.setUserId(userId);
    ur.setRoleId(roleId);
    userRoleMapper.insert(ur);
  }

  private void insertRoleMenu(Long roleId, Long menuId) {
    SysRoleMenu rm = new SysRoleMenu();
    rm.setRoleId(roleId);
    rm.setMenuId(menuId);
    roleMenuMapper.insert(rm);
  }
}
