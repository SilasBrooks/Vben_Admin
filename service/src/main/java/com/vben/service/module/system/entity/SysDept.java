package com.vben.service.module.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 部门表（树形结构，parentId 自引用，根部门 parentId=0）
 */
@Data
@TableName("sys_dept")
public class SysDept {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 父部门 id，0 = 根部门 */
  private Long parentId;

  private String deptName;

  /** 负责人姓名（仅存字段，暂不做人员选择器） */
  private String leader;

  /** 0 正常 1 停用 */
  private Integer status;

  private Integer orderNum;

  private String remark;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;

  /** 子部门（非表字段，仅用于树形接口返回） */
  @TableField(exist = false)
  private List<SysDept> children;
}
