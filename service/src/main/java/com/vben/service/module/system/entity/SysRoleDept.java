package com.vben.service.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色自定义数据部门关联表（仅角色 dataScope=2 时读写）。
 * 语义：所选部门含其全部子孙部门。
 */
@Data
@TableName("sys_role_dept")
public class SysRoleDept {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long roleId;

  private Long deptId;
}
