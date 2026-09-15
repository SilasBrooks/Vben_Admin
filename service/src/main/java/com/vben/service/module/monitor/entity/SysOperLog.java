package com.vben.service.module.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志表（由 @OperLog 注解 + 切面异步写入）
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 操作人 id（NULL=匿名/未登录） */
  private Long operUserId;

  private String operName;

  /** 所属模块，如「用户管理」 */
  private String module;

  /** 操作描述，如「新增用户」 */
  private String description;

  /** 调用方法：类名#方法名 */
  private String method;

  /** HTTP 方法 */
  private String requestMethod;

  private String requestUrl;

  /** 入参摘要（敏感字段脱敏 + 截断 2000） */
  private String params;

  /** 0 成功 1 失败 */
  private Integer status;

  /** 失败错误信息摘要 */
  private String errorMsg;

  private String ip;

  /** 耗时毫秒 */
  private Long costMs;

  private LocalDateTime operTime;
}
