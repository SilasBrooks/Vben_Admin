package com.vben.service.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色表
 */
@Data
@TableName("sys_role")
public class SysRole {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 角色标识，对应前端 roles 数组元素（如 super / admin / user） */
  private String roleKey;

  private String roleName;

  private Integer sortNum;

  /** 0 正常 1 停用 */
  private Integer status;

  private String remark;

  private LocalDateTime createTime;
}
