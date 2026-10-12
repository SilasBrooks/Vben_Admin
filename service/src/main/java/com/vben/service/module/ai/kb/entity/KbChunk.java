package com.vben.service.module.ai.kb.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库向量块：文档切块后的最小检索单元。
 *
 * <p>embedding 为 pgvector 无维度列，JDBC 无法直接映射为 Java 类型，
 * 写入与检索全部走 {@link com.vben.service.module.ai.kb.mapper.KbChunkMapper}
 * 的显式 CAST SQL；本实体仅用于按文档级联删除等无向量操作。
 */
@Data
@TableName("kb_chunk")
public class KbChunk {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long docId;

  /** 块在文档内的序号（0 起） */
  private Integer seq;

  private String content;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}
