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
 * 用户表
 */
@Data
@TableName("sys_user")
public class SysUser {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String username;

  /** BCrypt 哈希 */
  private String password;

  /** 显示名，对应前端 realName */
  private String nickname;

  /** 登录后首页（可选） */
  private String homePath;

  /** 所属部门 id（可选，NULL = 未归属部门） */
  private Long deptId;

  /** 头像文件 id（sys_file.id，可选，NULL = 未设置） */
  private String avatar;

  /** 个人简介（可选，NULL = 未填写） */
  private String introduction;

  /** 邮箱（可选，用于展示与联系） */
  private String email;

  /** 0 正常 1 停用 */
  private Integer status;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;

  /** 已分配角色 id 列表（非表字段，仅用于 save/update 入参与 detail 返回） */
  @TableField(exist = false)
  private List<Long> roleIds;

  /** 部门名称（非表字段，列表/详情展示用，service 层批量填充） */
  @TableField(exist = false)
  private String deptName;
}
