package com.vben.service.module.im.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * IM 单聊消息表（一条记录对应一条私聊消息，落库为准 + WebSocket 实时推送）
 */
@Data
@TableName("sys_message")
public class SysMessage {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 发送人 id（sys_user.id） */
  private Long senderId;

  /** 接收人 id（sys_user.id） */
  private Long receiverId;

  /** 消息内容（纯文本，最长 2000） */
  private String content;

  /** 已读标记（以接收人视角）：0 未读 1 已读 */
  private Integer readFlag;

  /** 阅读时间（接收人标记已读时间，未读为 null） */
  private LocalDateTime readTime;

  /** 发送人侧删除标记：0 否 1 已删（单侧删除，对方仍可见） */
  private Integer senderDeleted;

  /** 接收人侧删除标记：0 否 1 已删（单侧删除，对方仍可见） */
  private Integer receiverDeleted;

  /** 被引用消息 id（null=非引用消息；删除原消息不影响引用展示，展示用快照） */
  private Long quoteId;

  /** 被引用消息内容快照（发送时固化，原消息删除后引用仍可显示） */
  private String quoteContent;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  /** 更新时间由 MetaObjectHandler 填充（不使用数据库触发器，跨库通用） */
  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
