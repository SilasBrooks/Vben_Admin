package com.vben.service.module.ai.kb.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库分片上传会话：大文件分片协议的状态载体（24h 过期，定时清理）。
 *
 * <p>receivedChunks 为 jsonb int 数组，实体以 JSON 字符串承接读取；
 * 写入一律走 {@link com.vben.service.module.ai.kb.mapper.KbUploadSessionMapper}
 * 的显式 CAST SQL 与原子追加，避免 BaseMapper 直插 jsonb 类型不匹配。
 */
@Data
@TableName("kb_upload_session")
public class KbUploadSession {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 服务端 UUID，同时作为分片临时目录名 */
  private String uploadId;

  /** 客户端声明的整文件 SHA-256（complete 时校验） */
  private String sha256;

  private String fileName;

  private Long fileSize;

  /** 单片大小（字节，1-10MB） */
  private Long chunkSize;

  private Integer totalChunks;

  /** 已收分片索引（jsonb 数组的 JSON 文本，如 "[0,1,2]"） */
  private String receivedChunks;

  /** 0=上传中 1=已完成 2=已放弃 */
  private Integer status;

  private Long userId;

  /** 过期时间（创建 + 24h） */
  private LocalDateTime expireAt;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
