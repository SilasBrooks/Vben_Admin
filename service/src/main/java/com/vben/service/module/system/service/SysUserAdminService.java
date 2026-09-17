package com.vben.service.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vben.service.common.BizException;
import com.vben.service.common.DataScope;
import com.vben.service.common.DataScopeHolder;
import com.vben.service.module.system.entity.SysRole;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.entity.SysUserRole;
import com.vben.service.module.system.mapper.SysRoleMapper;
import com.vben.service.module.system.mapper.SysUserMapper;
import com.vben.service.module.system.mapper.SysUserRoleMapper;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import com.vben.service.security.TokenVersionService;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户后台管理：分页/详情/新建/编辑/删除/重置密码/分配角色。
 *
 * <p>保护规则：
 * <ul>
 *   <li>禁止删除当前登录用户（防自删）</li>
 *   <li>禁止删除/解绑「最后一个 super 角色用户」（防系统失能）</li>
 *   <li>password 字段一律不外泄，详情/列表返回前清空</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SysUserAdminService extends ServiceImpl<SysUserMapper, SysUser> {

  private static final String SUPER_ROLE_KEY = "super";

  private final SysUserRoleMapper userRoleMapper;
  private final SysRoleMapper roleMapper;
  private final SysDeptAdminService deptService;
  private final TokenVersionService tokenVersionService;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  /** 用户分页列表（密码字段已清空，支持按用户名模糊 + 状态过滤；回填部门名；按数据范围过滤） */
  @DataScope
  public IPage<SysUser> page(long pageNo, long pageSize, String username, Integer status) {
    LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<SysUser>()
        .orderByDesc(SysUser::getCreateTime);
    if (username != null && !username.isBlank()) {
      q.like(SysUser::getUsername, username);
    }
    if (status != null) {
      q.eq(SysUser::getStatus, status);
    }
    applyDataScope(q);
    IPage<SysUser> p = page(new Page<>(pageNo, pageSize), q);
    p.getRecords().forEach(u -> u.setPassword(null));
    deptService.fillDeptNames(p.getRecords());
    return p;
  }

  /**
   * 按数据范围上下文追加过滤条件：
   * SELF → id=本人；DEPT → dept_id IN (...) OR dept_id IS NULL（视 includeUnassigned）OR id=本人；
   * ALL/无上下文 → 不追加。必须置于一个嵌套括号内，避免与外层 keyword/status 条件混淆 AND/OR 优先级。
   */
  private void applyDataScope(LambdaQueryWrapper<SysUser> q) {
    DataScopeHolder.DataScopeInfo ds = DataScopeHolder.get();
    if (ds == null || ds.type() == DataScopeHolder.Type.ALL) {
      return;
    }
    q.and(w -> {
      if (ds.type() == DataScopeHolder.Type.SELF) {
        w.eq(SysUser::getId, ds.userId());
        return;
      }
      boolean first = true;
      if (!ds.deptIds().isEmpty()) {
        w.in(SysUser::getDeptId, ds.deptIds());
        first = false;
      }
      if (ds.includeUnassigned()) {
        if (!first) {
          w.or();
        }
        w.isNull(SysUser::getDeptId);
        first = false;
      }
      if (ds.userId() != null) {
        if (!first) {
          w.or();
        }
        w.eq(SysUser::getId, ds.userId());
      }
    });
  }

  /** 用户详情（含已分配角色 id 列表 + 部门名，密码字段已清空） */
  public SysUser detail(Long id) {
    SysUser user = getById(id);
    if (user == null) {
      return null;
    }
    user.setPassword(null);
    user.setRoleIds(baseMapper.selectRoleIdsByUserId(id));
    deptService.fillDeptNames(List.of(user));
    return user;
  }

  /** 用户已分配角色 id 列表（编辑回显） */
  public List<Long> roleIds(Long userId) {
    if (getById(userId) == null) {
      throw BizException.badRequest("用户不存在");
    }
    return baseMapper.selectRoleIdsByUserId(userId);
  }

  /** 新建用户：校验用户名唯一性，BCrypt 加密密码，落库 + 分配角色 */
  @Transactional
  public void saveUser(SysUser user) {
    if (user.getUsername() == null || user.getUsername().isBlank()) {
      throw BizException.badRequest("用户名不能为空");
    }
    if (user.getPassword() == null || user.getPassword().isBlank()) {
      throw BizException.badRequest("初始密码不能为空");
    }
    long exists = count(new LambdaQueryWrapper<SysUser>()
        .eq(SysUser::getUsername, user.getUsername()));
    if (exists > 0) {
      throw BizException.badRequest("用户名已存在");
    }
    if (user.getStatus() == null) {
      user.setStatus(0);
    }
    checkDept(user.getDeptId());
    String rawPassword = user.getPassword();
    user.setPassword(passwordEncoder.encode(rawPassword));
    save(user);
    // 分配角色（若未传则空集合）
    List<Long> roleIds = user.getRoleIds() == null ? Collections.emptyList() : user.getRoleIds();
    reassignRoles(user.getId(), roleIds);
  }

  /** 编辑用户：仅更新非密码字段，重新分配角色（保护最后一个 super） */
  @Transactional
  public void updateUser(SysUser user) {
    SysUser exist = getById(user.getId());
    if (exist == null) {
      throw BizException.badRequest("用户不存在");
    }
    // 用户名不允许修改（避免唯一键冲突 + 关联历史日志脱钩）
    user.setUsername(exist.getUsername());
    // password 由 resetPassword 单独接口处理，此处忽略
    user.setPassword(null);
    if (user.getStatus() == null) {
      user.setStatus(exist.getStatus());
    }
    checkDept(user.getDeptId());
    updateById(user);
    // 停用用户：版本 +1，其已签发 token 立即失效（被保护逻辑已确保不会停用最后一个 super）
    if (exist.getStatus() != null && exist.getStatus() == 0 && user.getStatus() == 1) {
      tokenVersionService.bump(user.getId());
    }

    // 仅当显式传了 roleIds 才重分配；不传 = 不动角色（避免误清空）
    if (user.getRoleIds() != null) {
      List<Long> roleIds = user.getRoleIds();
      protectLastSuper(user.getId(), exist, user, roleIds);
      reassignRoles(user.getId(), roleIds);
    }
  }

  /** 删除用户：禁止自删、禁止删除最后一个 super 用户 */
  @Transactional
  public void remove(Long id) {
    LoginUser current = LoginUserHolder.get();
    if (current != null && id.equals(current.getUserId())) {
      throw BizException.badRequest("不能删除当前登录用户");
    }
    SysUser exist = getById(id);
    if (exist == null) {
      throw BizException.badRequest("用户不存在");
    }
    // 若该用户拥有 super 角色，且是系统最后一个有效 super 用户 → 阻止
    List<Long> currentUserRoleIds = baseMapper.selectRoleIdsByUserId(id);
    if (containsSuperRole(currentUserRoleIds) && baseMapper.countActiveUsersByRoleKey(SUPER_ROLE_KEY) <= 1) {
      throw BizException.badRequest("不能删除系统最后一个 super 用户");
    }
    userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
    removeById(id);
  }

  /** 重置密码：仅 super / 当前用户本人可改（前端按按钮权限控制） */
  @Transactional
  public void resetPassword(Long userId, String newPassword) {
    SysUser exist = getById(userId);
    if (exist == null) {
      throw BizException.badRequest("用户不存在");
    }
    if (newPassword == null || newPassword.length() < 6) {
      throw BizException.badRequest("新密码至少 6 位");
    }
    SysUser patch = new SysUser();
    patch.setId(userId);
    patch.setPassword(passwordEncoder.encode(newPassword));
    updateById(patch);
    // 重置密码成功：版本 +1，目标用户已签发 token 立即失效
    tokenVersionService.bump(userId);
  }

  /** 重新分配用户角色（先删后插） */
  @Transactional
  public void assignRoles(Long userId, List<Long> roleIds) {
    SysUser exist = getById(userId);
    if (exist == null) {
      throw BizException.badRequest("用户不存在");
    }
    List<Long> newRoleIds = roleIds == null ? Collections.emptyList() : roleIds;
    protectLastSuper(userId, exist, exist, newRoleIds);
    reassignRoles(userId, newRoleIds);
  }

  // ------------------------------------------------------------------
  // 内部实现
  // ------------------------------------------------------------------

  /** 校验用户归属的部门存在且未停用（null = 不归属部门，放行） */
  private void checkDept(Long deptId) {
    if (deptId == null) {
      return;
    }
    com.vben.service.module.system.entity.SysDept dept = deptService.getById(deptId);
    if (dept == null) {
      throw BizException.badRequest("所选部门不存在");
    }
    if (dept.getStatus() != null && dept.getStatus() == 1) {
      throw BizException.badRequest("所选部门已停用");
    }
  }

  /** 真正执行角色重分配：先删 user_role，再批量插 */
  private void reassignRoles(Long userId, List<Long> roleIds) {
    userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
    for (Long roleId : roleIds) {
      SysUserRole ur = new SysUserRole();
      ur.setUserId(userId);
      ur.setRoleId(roleId);
      userRoleMapper.insert(ur);
    }
  }

  /**
   * 保护最后一个 super 用户：
   * - 若用户原本有 super 角色，且新角色集合移除了 super → 检查是否最后一个
   * - 若用户原本正常，新状态变为停用 且拥有 super → 检查是否最后一个
   */
  private void protectLastSuper(Long userId, SysUser oldUser, SysUser newUser, List<Long> newRoleIds) {
    List<Long> oldRoleIds = baseMapper.selectRoleIdsByUserId(userId);
    if (!containsSuperRole(oldRoleIds)) {
      return;
    }
    boolean stillHasSuper = containsSuperRole(newRoleIds);
    boolean beingDisabled = newUser.getStatus() != null && newUser.getStatus() == 1
        && (oldUser.getStatus() == null || oldUser.getStatus() == 0);
    if (stillHasSuper && !beingDisabled) {
      return;
    }
    if (baseMapper.countActiveUsersByRoleKey(SUPER_ROLE_KEY) <= 1) {
      throw BizException.badRequest("不能移除/停用系统最后一个 super 用户");
    }
  }

  /** 角色id列表 → 角色key集合（用于判断是否含 super） */
  private Set<String> toRoleKeys(List<Long> roleIds) {
    if (roleIds == null || roleIds.isEmpty()) {
      return Collections.emptySet();
    }
    List<SysRole> roles = roleMapper.selectList(
        new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds));
    Set<String> keys = new HashSet<>();
    for (SysRole r : roles) {
      keys.add(r.getRoleKey());
    }
    return keys;
  }

  /** 给定角色 id 列表，判断是否包含 super 角色 */
  private boolean containsSuperRole(List<Long> roleIds) {
    return toRoleKeys(roleIds).contains(SUPER_ROLE_KEY);
  }
}
