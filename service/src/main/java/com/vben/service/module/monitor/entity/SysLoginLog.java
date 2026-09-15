package com.vben.service.module.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志表（每次登录尝试成功/失败各记一条）
 */
@Data
@TableName("sys_login_log")
public class SysLoginLog {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 尝试登录的用户名（原文，用户可能不存在） */
  private String username;

  /** 0 成功 1 失败 */
  private Integer status;

  /** 结果消息 */
  private String message;

  private String ip;

  private String userAgent;

  private LocalDateTime loginTime;
}
