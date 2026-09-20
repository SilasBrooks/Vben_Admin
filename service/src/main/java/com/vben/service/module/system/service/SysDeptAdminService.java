package com.vben.service.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vben.service.common.BizException;
import com.vben.service.module.system.entity.SysDept;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysDeptMapper;
import com.vben.service.module.system.mapper.SysUserMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 部门后台管理：树查询/新增/修改/删除。
 *
 * <p>约束规则：
 * <ul>
 *   <li>同级部门名称唯一（同 parentId 下 deptName 不重复）</li>
 *   <li>父部门必须存在且未停用（根部门 parentId = 0）</li>
 *   <li>修改时防环：不允许把部门挂到自身或自己的子孙节点下</li>
 *   <li>删除前校验：无子部门，且没有用户归属该部门</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SysDeptAdminService extends ServiceImpl<SysDeptMapper, SysDept> {

  private final SysUserMapper userMapper;

  /** 部门树（按 parentId 聚合成 children，orderNum 升序） */
  public List<SysDept> tree() {
    List<SysDept> all = list(new LambdaQueryWrapper<SysDept>()
        .orderByAsc(SysDept::getParentId)
        .orderByAsc(SysDept::getOrderNum));
    return buildTree(all, 0L);
  }

  public SysDept detail(Long id) {
    return getById(id);
  }

  /** 新增部门：父部门合法性 + 同级重名校验 */
  @Transactional
  public void saveDept(SysDept dept) {
    if (dept.getDeptName() == null || dept.getDeptName().isBlank()) {
      throw BizException.badRequest("error.dept.name.blank");
    }
    Long parentId = dept.getParentId() == null ? 0L : dept.getParentId();
    dept.setParentId(parentId);
    if (parentId != 0) {
      SysDept parent = getById(parentId);
      if (parent == null) {
        throw BizException.badRequest("error.dept.parent.notFound");
      }
      if (parent.getStatus() != null && parent.getStatus() == 1) {
        throw BizException.badRequest("error.dept.parent.disabledAdd");
      }
    }
    checkSiblingNameUnique(parentId, dept.getDeptName(), null);
    if (dept.getStatus() == null) {
      dept.setStatus(0);
    }
    if (dept.getOrderNum() == null) {
      dept.setOrderNum(1);
    }
    save(dept);
  }

  /** 修改部门：父部门合法性 + 防环 + 同级重名校验 */
  @Transactional
  public void updateDept(SysDept dept) {
    SysDept exist = getById(dept.getId());
    if (exist == null) {
      throw BizException.badRequest("error.dept.notFound");
    }
    if (dept.getDeptName() == null || dept.getDeptName().isBlank()) {
      throw BizException.badRequest("error.dept.name.blank");
    }
    Long parentId = dept.getParentId() == null ? 0L : dept.getParentId();
    dept.setParentId(parentId);
    if (Objects.equals(parentId, dept.getId())) {
      throw BizException.badRequest("error.dept.parent.self");
    }
    if (parentId != 0) {
      SysDept parent = getById(parentId);
      if (parent == null) {
        throw BizException.badRequest("error.dept.parent.notFound");
      }
      if (parent.getStatus() != null && parent.getStatus() == 1) {
        throw BizException.badRequest("error.dept.parent.disabledMove");
      }
    }
    // 防环：沿新 parentId 向上遍历祖先链，出现自身 id 即拒绝
    checkCycle(dept.getId(), parentId);
    checkSiblingNameUnique(parentId, dept.getDeptName(), dept.getId());
    if (dept.getStatus() == null) {
      dept.setStatus(exist.getStatus());
    }
    updateById(dept);
  }

  /** 删除部门：无子部门 且 无用户归属 才允许删除 */
  @Transactional
  public void remove(Long id) {
    if (getById(id) == null) {
      throw BizException.badRequest("error.dept.notFound");
    }
    long children = count(new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, id));
    if (children > 0) {
      throw BizException.badRequest("error.dept.hasChildren");
    }
    long users = userMapper.selectCount(
        new LambdaQueryWrapper<SysUser>().eq(SysUser::getDeptId, id));
    if (users > 0) {
      throw BizException.badRequest("error.dept.hasUsers");
    }
    removeById(id);
  }

  /** 批量查部门名并回填（用户列表/详情展示用） */
  public void fillDeptNames(List<SysUser> users) {
    List<Long> deptIds = users.stream()
        .map(SysUser::getDeptId)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
    if (deptIds.isEmpty()) {
      return;
    }
    List<SysDept> depts = listByIds(deptIds);
    var nameMap = depts.stream()
        .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName));
    for (SysUser u : users) {
      if (u.getDeptId() != null) {
        u.setDeptName(nameMap.get(u.getDeptId()));
      }
    }
  }

  // ------------------------------------------------------------------
  // 内部实现
  // ------------------------------------------------------------------

  /** 同级部门名称唯一校验（excludeId 用于编辑时排除自身） */
  private void checkSiblingNameUnique(Long parentId, String deptName, Long excludeId) {
    long dup = count(new LambdaQueryWrapper<SysDept>()
        .eq(SysDept::getParentId, parentId)
        .eq(SysDept::getDeptName, deptName)
        .ne(excludeId != null, SysDept::getId, excludeId));
    if (dup > 0) {
      throw BizException.badRequest("error.dept.name.duplicate");
    }
  }

  /** 防环校验：从新 parentId 沿祖先链向上走，出现 deptId 即说明会形成环 */
  private void checkCycle(Long deptId, Long newParentId) {
    Long cursor = newParentId;
    List<Long> visited = new ArrayList<>();
    while (cursor != null && cursor != 0) {
      if (cursor.equals(deptId)) {
        throw BizException.badRequest("error.dept.move.invalid");
      }
      if (visited.contains(cursor)) {
        // 理论上不该出现的数据环，兜底防死循环
        break;
      }
      visited.add(cursor);
      SysDept parent = getById(cursor);
      cursor = parent == null ? null : parent.getParentId();
    }
  }

  /** 根据父 id 递归构建树 */
  private List<SysDept> buildTree(List<SysDept> all, Long parentId) {
    return all.stream()
        .filter(d -> Objects.equals(d.getParentId(), parentId))
        .sorted(Comparator.comparing(SysDept::getOrderNum,
            Comparator.nullsLast(Integer::compareTo)))
        .peek(d -> d.setChildren(buildTree(all, d.getId())))
        .collect(Collectors.toList());
  }
}
