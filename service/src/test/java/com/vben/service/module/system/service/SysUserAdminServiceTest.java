package com.vben.service.module.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vben.service.common.BizException;
import com.vben.service.module.notice.service.NoticeService;
import com.vben.service.module.system.entity.SysDept;
import com.vben.service.module.system.entity.SysRole;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysRoleMapper;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.module.system.mapper.SysUserRoleMapper;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import com.vben.service.security.TokenVersionService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * SysUserAdminService 核心保护规则与副作用单测（纯 Mockito，无容器/数据库依赖）。
 *
 * <p>覆盖：新建校验与密码加密、部门校验、停用触发的 token 版本 bump + 通知、
 * 禁止自删、最后一个 super 用户保护、重置密码校验与 bump。
 */
class SysUserAdminServiceTest {

  private SysUserMapper userMapper;
  private SysUserRoleMapper userRoleMapper;
  private SysRoleMapper roleMapper;
  private SysDeptAdminService deptService;
  private TokenVersionService tokenVersionService;
  private NoticeService noticeService;
  private PermissionCacheService permissionCacheService;
  private SysUserAdminService service;

  @BeforeEach
  void setUp() {
    userMapper = mock(SysUserMapper.class);
    userRoleMapper = mock(SysUserRoleMapper.class);
    roleMapper = mock(SysRoleMapper.class);
    deptService = mock(SysDeptAdminService.class);
    tokenVersionService = mock(TokenVersionService.class);
    noticeService = mock(NoticeService.class);
    permissionCacheService = mock(PermissionCacheService.class);
    service = new SysUserAdminService(userRoleMapper, roleMapper, deptService,
        tokenVersionService, noticeService, permissionCacheService);
    // ServiceImpl 的 baseMapper 由 Spring 启动时注入，单测需手动反射注入 mock
    ReflectionTestUtils.setField(service, "baseMapper", userMapper);
  }

  @AfterEach
  void tearDown() {
    LoginUserHolder.clear();
  }

  /** 抛 BizException 并返回异常（供 status/message 断言；无容器时 message=key 原样） */
  private static BizException biz(Runnable action) {
    return assertThrows(BizException.class, action::run);
  }

  private static SysUser user(Long id, String username, String password) {
    SysUser u = new SysUser();
    u.setId(id);
    u.setUsername(username);
    u.setPassword(password);
    return u;
  }

  private static SysRole role(Long id, String key) {
    SysRole r = new SysRole();
    r.setId(id);
    r.setRoleKey(key);
    return r;
  }

  // ---------------- saveUser ----------------

  @Test
  void saveUser_blankUsername_rejected() {
    BizException ex = biz(() -> service.saveUser(user(null, "  ", "123456")));
    assertEquals(400, ex.getStatus());
    assertEquals("error.user.username.blank", ex.getMessage());
  }

  @Test
  void saveUser_blankPassword_rejected() {
    BizException ex = biz(() -> service.saveUser(user(null, "neo", " ")));
    assertEquals(400, ex.getStatus());
    assertEquals("error.user.password.blank", ex.getMessage());
  }

  @Test
  void saveUser_usernameExists_rejected() {
    when(userMapper.selectCount(any())).thenReturn(1L);
    BizException ex = biz(() -> service.saveUser(user(null, "vben", "123456")));
    assertEquals(400, ex.getStatus());
    assertEquals("error.user.username.exists", ex.getMessage());
    verify(userMapper, never()).insert(any(SysUser.class));
  }

  @Test
  void saveUser_success_encodesPasswordAndAssignsRoles() {
    when(userMapper.selectCount(any())).thenReturn(0L);
    SysUser input = user(null, "neo", "123456");
    input.setRoleIds(List.of(2L, 3L));

    service.saveUser(input);

    ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
    verify(userMapper).insert(captor.capture());
    SysUser saved = captor.getValue();
    // 密码必须 BCrypt 加密，不再是明文；默认启用状态
    assertTrue(new BCryptPasswordEncoder().matches("123456", saved.getPassword()));
    assertEquals(0, saved.getStatus());
    // 角色：先清空后逐个插入
    verify(userRoleMapper).delete(any());
    verify(userRoleMapper, times(2)).insert(any(com.vben.service.module.system.entity.SysUserRole.class));
  }

  @Test
  void saveUser_missingDept_rejected() {
    when(userMapper.selectCount(any())).thenReturn(0L);
    when(deptService.getById(9L)).thenReturn(null);
    SysUser input = user(null, "neo", "123456");
    input.setDeptId(9L);
    BizException ex = biz(() -> service.saveUser(input));
    assertEquals(400, ex.getStatus());
    assertEquals("error.user.dept.notFound", ex.getMessage());
  }

  @Test
  void saveUser_disabledDept_rejected() {
    when(userMapper.selectCount(any())).thenReturn(0L);
    SysDept dept = new SysDept();
    dept.setId(4L);
    dept.setStatus(1);
    when(deptService.getById(4L)).thenReturn(dept);
    SysUser input = user(null, "neo", "123456");
    input.setDeptId(4L);
    BizException ex = biz(() -> service.saveUser(input));
    assertEquals(400, ex.getStatus());
    assertEquals("error.user.dept.disabled", ex.getMessage());
  }

  // ---------------- updateUser ----------------

  @Test
  void updateUser_disableUser_bumpsTokenVersionAndNotifies() {
    SysUser exist = user(2L, "jack", "x");
    exist.setStatus(0);
    when(userMapper.selectById(2L)).thenReturn(exist);
    SysUser patch = new SysUser();
    patch.setId(2L);
    patch.setStatus(1);

    service.updateUser(patch);

    // 停用：版本 bump + 当事人通知，旧 token 立即失效
    verify(tokenVersionService).bump(2L);
    verify(noticeService).send(eq(2L), anyString(), anyString());
    verify(userMapper).updateById(patch);
    // roleIds 未传：不动角色
    verify(userRoleMapper, never()).delete(any());
  }

  @Test
  void updateUser_statusUnchanged_noBump() {
    SysUser exist = user(2L, "jack", "x");
    exist.setStatus(0);
    when(userMapper.selectById(2L)).thenReturn(exist);
    SysUser patch = new SysUser();
    patch.setId(2L);

    service.updateUser(patch);

    verify(tokenVersionService, never()).bump(anyLong());
    verify(noticeService, never()).send(anyLong(), anyString(), anyString());
  }

  @Test
  void updateUser_notFound_rejected() {
    when(userMapper.selectById(99L)).thenReturn(null);
    SysUser patch = new SysUser();
    patch.setId(99L);
    BizException ex = biz(() -> service.updateUser(patch));
    assertEquals(400, ex.getStatus());
    assertEquals("error.user.notFound", ex.getMessage());
  }

  // ---------------- remove ----------------

  @Test
  void remove_selfDelete_rejected() {
    LoginUserHolder.set(new LoginUser(2L, "jack", List.of("user"), null, 0));
    BizException ex = biz(() -> service.remove(2L));
    assertEquals(400, ex.getStatus());
    assertEquals("error.user.delete.self", ex.getMessage());
  }

  @Test
  void remove_lastSuperUser_rejected() {
    when(userMapper.selectById(3L)).thenReturn(user(3L, "vben2", "x"));
    when(userMapper.selectRoleIdsByUserId(3L)).thenReturn(List.of(1L));
    when(roleMapper.selectList(any())).thenReturn(List.of(role(1L, "super")));
    when(userMapper.countActiveUsersByRoleKey("super")).thenReturn(1L);

    BizException ex = biz(() -> service.remove(3L));
    assertEquals(400, ex.getStatus());
    assertEquals("error.user.lastSuper", ex.getMessage());
    verify(userMapper, never()).deleteById(anyLong());
  }

  @Test
  void remove_normalUser_deletesRolesAndUser() {
    when(userMapper.selectById(3L)).thenReturn(user(3L, "jack", "x"));
    when(userMapper.selectRoleIdsByUserId(3L)).thenReturn(List.of(3L));
    when(roleMapper.selectList(any())).thenReturn(List.of(role(3L, "user")));

    service.remove(3L);

    verify(userRoleMapper).delete(any());
    verify(userMapper).deleteById(3L);
  }

  // ---------------- resetPassword ----------------

  @Test
  void resetPassword_tooShort_rejected() {
    when(userMapper.selectById(2L)).thenReturn(user(2L, "jack", "x"));
    BizException ex = biz(() -> service.resetPassword(2L, "12345"));
    assertEquals(400, ex.getStatus());
    assertEquals("error.auth.password.tooShort", ex.getMessage());
    verify(tokenVersionService, never()).bump(anyLong());
  }

  @Test
  void resetPassword_success_encodesBumpsAndNotifies() {
    when(userMapper.selectById(2L)).thenReturn(user(2L, "jack", "x"));

    service.resetPassword(2L, "newpass123");

    ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
    verify(userMapper).updateById(captor.capture());
    assertTrue(new BCryptPasswordEncoder().matches("newpass123", captor.getValue().getPassword()));
    verify(tokenVersionService).bump(2L);
    verify(noticeService).send(eq(2L), anyString(), anyString());
  }

  // ---------------- assignRoles ----------------

  @Test
  void assignRoles_downgradeLastSuper_rejected() {
    SysUser exist = user(3L, "vben2", "x");
    exist.setStatus(0);
    when(userMapper.selectById(3L)).thenReturn(exist);
    when(userMapper.selectRoleIdsByUserId(3L)).thenReturn(List.of(1L));
    // 连续打桩：第一次查旧角色集合（super），第二次查新角色集合（user）
    when(roleMapper.selectList(any()))
        .thenReturn(List.of(role(1L, "super")))
        .thenReturn(List.of(role(3L, "user")));
    when(userMapper.countActiveUsersByRoleKey("super")).thenReturn(1L);

    BizException ex = biz(() -> service.assignRoles(3L, List.of(3L)));
    assertEquals(400, ex.getStatus());
    assertEquals("error.user.lastSuper", ex.getMessage());
    verify(userRoleMapper, never()).delete(any());
  }

  // ---------------- roleIds ----------------

  @Test
  void roleIds_userNotFound_rejected() {
    when(userMapper.selectById(99L)).thenReturn(null);
    BizException ex = biz(() -> service.roleIds(99L));
    assertEquals(400, ex.getStatus());
    assertEquals("error.user.notFound", ex.getMessage());
  }

  // ---------------- 密码不外泄 ----------------

  @Test
  void detail_nullUser_returnsNull() {
    when(userMapper.selectById(99L)).thenReturn(null);
    assertNull(service.detail(99L));
  }
}
