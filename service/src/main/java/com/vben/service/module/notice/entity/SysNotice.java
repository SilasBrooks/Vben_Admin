package com.vben.service.module.notice.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知表（一条记录对应一个接收人，配合 WebSocket 实时推送）
 */
@Data
@TableName("sys_notice")
public class SysNotice {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 接收人 id（sys_user.id） */
  private Long userId;

  /** 通知标题 */
  private String title;

  /** 通知内容 */
  private String content;

  /** 消息类型：security 安全提醒（本期仅此一类） */
  private String msgType;

  /** 已读标记：0 未读 1 已读 */
  private Integer readFlag;

  /** 阅读时间（未读为 null） */
  private LocalDateTime readTime;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  /** 更新时间由 MetaObjectHandler 填充（不使用数据库触发器，跨库通用） */
  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
