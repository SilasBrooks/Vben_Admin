package com.vben.service.module.system.entity;

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

  /** 0 正常 1 停用 */
  private Integer status;

  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  /** 已分配角色 id 列表（非表字段，仅用于 save/update 入参与 detail 返回） */
  @TableField(exist = false)
  private List<Long> roleIds;
}
