package com.vben.service.module.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vben.service.common.BizException;
import com.vben.service.module.notice.service.NoticeService;
import com.vben.service.module.system.entity.SysRole;
import com.vben.service.module.system.entity.SysRoleDept;
import com.vben.service.module.system.entity.SysRoleMenu;
import com.vben.service.module.system.mapper.SysRoleDeptMapper;
import com.vben.service.module.system.mapper.SysRoleMapper;
import com.vben.service.module.system.mapper.SysRoleMenuMapper;
import com.vben.service.module.system.mapper.SysUserRoleMapper;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyString;

/**
 * SysRoleAdminService 核心保护规则单测（纯 Mockito，无容器/数据库依赖）。
 *
 * <p>覆盖：super 角色 roleKey 不可改 / 不可停用 / 不可删除、已分配用户角色不可删、
 * 数据范围校验（非法值 / super 恒为全部 / 自定义部门必选）、授权全量替换 + 当事人通知。
 */
class SysRoleAdminServiceTest {

  private SysRoleMapper roleMapper;
  private SysRoleMenuMapper roleMenuMapper;
  private SysUserRoleMapper userRoleMapper;
  private SysRoleDeptMapper roleDeptMapper;
  private NoticeService noticeService;
  private PermissionCacheService permissionCacheService;
  private SysRoleAdminService service;

  @BeforeEach
  void setUp() {
    roleMapper = mock(SysRoleMapper.class);
    roleMenuMapper = mock(SysRoleMenuMapper.class);
    userRoleMapper = mock(SysUserRoleMapper.class);
    roleDeptMapper = mock(SysRoleDeptMapper.class);
    noticeService = mock(NoticeService.class);
    permissionCacheService = mock(PermissionCacheService.class);
    service = new SysRoleAdminService(roleMenuMapper, userRoleMapper, roleDeptMapper,
        noticeService, permissionCacheService);
    ReflectionTestUtils.setField(service, "baseMapper", roleMapper);
  }

  private static BizException biz(Runnable action) {
    return org.junit.jupiter.api.Assertions.assertThrows(BizException.class, action::run);
  }

  private static SysRole role(Long id, String key, String dataScope) {
    SysRole r = new SysRole();
    r.setId(id);
    r.setRoleKey(key);
    r.setDataScope(dataScope);
    return r;
  }

  // ---------------- updateRole ----------------

  @Test
  void updateRole_notFound_rejected() {
    when(roleMapper.selectById(9L)).thenReturn(null);
    SysRole patch = new SysRole();
    patch.setId(9L);
    BizException ex = biz(() -> service.updateRole(patch));
    assertEquals(400, ex.getStatus());
    assertEquals("error.role.notFound", ex.getMessage());
  }

  @Test
  void updateRole_superKeyChange_rejected() {
    when(roleMapper.selectById(1L)).thenReturn(role(1L, "super", "1"));
    SysRole patch = new SysRole();
    patch.setId(1L);
    patch.setRoleKey("boss");
    BizException ex = biz(() -> service.updateRole(patch));
    assertEquals(400, ex.getStatus());
    assertEquals("error.role.superKeyFixed", ex.getMessage());
    verify(roleMapper, never()).updateById(any(SysRole.class));
  }

  @Test
  void updateRole_superDisable_rejected() {
    when(roleMapper.selectById(1L)).thenReturn(role(1L, "super", "1"));
    SysRole patch = new SysRole();
    patch.setId(1L);
    patch.setStatus(1);
    BizException ex = biz(() -> service.updateRole(patch));
    assertEquals(400, ex.getStatus());
    assertEquals("error.role.superCannotDisable", ex.getMessage());
    verify(roleMapper, never()).updateById(any(SysRole.class));
  }

  @Test
  void updateRole_normalRole_updated() {
    when(roleMapper.selectById(2L)).thenReturn(role(2L, "admin", "1"));
    SysRole patch = new SysRole();
    patch.setId(2L);
    patch.setRoleName("管理员");

    service.updateRole(patch);

    verify(roleMapper).updateById(patch);
  }

  // ---------------- remove ----------------

  @Test
  void remove_superRole_rejected() {
    when(roleMapper.selectById(1L)).thenReturn(role(1L, "super", "1"));
    BizException ex = biz(() -> service.remove(1L));
    assertEquals(400, ex.getStatus());
    assertEquals("error.role.superCannotDelete", ex.getMessage());
  }

  @Test
  void remove_roleInUse_rejected() {
    when(roleMapper.selectById(2L)).thenReturn(role(2L, "admin", "1"));
    when(userRoleMapper.selectCount(any())).thenReturn(2L);
    BizException ex = biz(() -> service.remove(2L));
    assertEquals(400, ex.getStatus());
    assertEquals("error.role.inUse", ex.getMessage());
    verify(roleMapper, never()).deleteById(anyLong());
  }

  @Test
  void remove_unusedRole_clearsRoleMenuAndRole() {
    when(roleMapper.selectById(3L)).thenReturn(role(3L, "user", "5"));
    when(userRoleMapper.selectCount(any())).thenReturn(0L);

    service.remove(3L);

    verify(roleMenuMapper).delete(any());
    verify(roleMapper).deleteById(3L);
  }

  // ---------------- updateDataScope ----------------

  @Test
  void updateDataScope_invalidValue_rejected() {
    when(roleMapper.selectById(2L)).thenReturn(role(2L, "admin", "1"));
    BizException ex = biz(() -> service.updateDataScope(2L, "9", null));
    assertEquals(400, ex.getStatus());
    assertEquals("error.role.dataScope.invalid", ex.getMessage());
  }

  @Test
  void updateDataScope_multiCharValue_rejected() {
    when(roleMapper.selectById(2L)).thenReturn(role(2L, "admin", "1"));
    BizException ex = biz(() -> service.updateDataScope(2L, "12", null));
    assertEquals(400, ex.getStatus());
    assertEquals("error.role.dataScope.invalid", ex.getMessage());
  }

  @Test
  void updateDataScope_superNotAll_rejected() {
    when(roleMapper.selectById(1L)).thenReturn(role(1L, "super", "1"));
    BizException ex = biz(() -> service.updateDataScope(1L, "2", List.of(2L)));
    assertEquals(400, ex.getStatus());
    assertEquals("error.role.dataScope.superFixed", ex.getMessage());
  }

  @Test
  void updateDataScope_customWithoutDepts_rejected() {
    when(roleMapper.selectById(4L)).thenReturn(role(4L, "stock", "2"));
    BizException ex = biz(() -> service.updateDataScope(4L, "2", Collections.emptyList()));
    assertEquals(400, ex.getStatus());
    assertEquals("error.role.dataScope.deptRequired", ex.getMessage());
  }

  @Test
  void updateDataScope_custom_savesDeptBindings() {
    when(roleMapper.selectById(4L)).thenReturn(role(4L, "stock", "1"));

    service.updateDataScope(4L, "2", List.of(2L, 4L));

    // 先清空旧绑定，再按新集合逐个插入；dataScope 落库
    verify(roleDeptMapper).delete(any());
    verify(roleDeptMapper, times(2)).insert(any(SysRoleDept.class));
    ArgumentCaptor<SysRole> captor = ArgumentCaptor.forClass(SysRole.class);
    verify(roleMapper).updateById(captor.capture());
    assertEquals("2", captor.getValue().getDataScope());
  }

  @Test
  void updateDataScope_nonCustom_clearsDeptBindings() {
    when(roleMapper.selectById(3L)).thenReturn(role(3L, "user", "2"));

    service.updateDataScope(3L, "5", null);

    verify(roleDeptMapper).delete(any());
    verify(roleDeptMapper, never()).insert(any(SysRoleDept.class));
  }

  // ---------------- assignMenus ----------------

  @Test
  void assignMenus_rebuildsBindingsAndNotifiesRoleUsers() {
    when(roleMapper.selectById(2L)).thenReturn(role(2L, "admin", "1"));
    when(userRoleMapper.selectUserIdsByRoleId(2L)).thenReturn(List.of(1L, 3L));

    service.assignMenus(2L, List.of(10L, 11L, 12L));

    // 全量替换语义：先删后插
    verify(roleMenuMapper).delete(any());
    verify(roleMenuMapper, times(3)).insert(any(SysRoleMenu.class));
    // 角色下每个用户都收到权限变更通知
    verify(noticeService, times(2)).send(anyLong(), eq("您的功能权限已更新"), anyString());
  }

  @Test
  void assignMenus_roleNotFound_rejected() {
    when(roleMapper.selectById(9L)).thenReturn(null);
    BizException ex = biz(() -> service.assignMenus(9L, List.of(1L)));
    assertEquals(400, ex.getStatus());
    assertEquals("error.role.notFound", ex.getMessage());
  }
}
