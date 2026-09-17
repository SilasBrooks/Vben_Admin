package com.vben.service.module.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件记录表：一条记录对应一个物理文件（存储实现内部标识为 storage_key）
 */
@Data
@TableName("sys_file")
public class SysFile {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 原始文件名 */
  private String originalName;

  /** 存储键（存储实现内部标识，如 日期分桶/UUID.ext） */
  private String storageKey;

  /** 文件大小（字节） */
  private Long size;

  /** 内容类型（上传时由原始名推导，非客户端可控字段） */
  private String contentType;

  /** 业务类型：avatar 头像 general 通用 */
  private String bizType;

  /** 上传人 id（sys_user.id） */
  private Long uploaderId;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  /** 上传人用户名（非表字段，列表展示用，service 层批量填充） */
  @TableField(exist = false)
  private String uploaderName;
}
