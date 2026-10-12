package com.vben.service.module.ai.kb.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库物理文件：SHA-256 去重载体，秒传判定依据。
 *
 * <p>同一内容文件只存一份（StorageService），多个文档经 ref_count 引用；
 * 引用归零时清理存储文件与本记录（删除文档时维护）。
 */
@Data
@TableName("kb_file")
public class KbFile {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 整文件 SHA-256（64 位十六进制，唯一索引） */
  private String sha256;

  /** 文件大小（字节） */
  private Long size;

  /** StorageService 存储键 */
  private String storageKey;

  /** 引用该文件的文档数 */
  private Integer refCount;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}
