package com.vben.service.module.ai.kb.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 知识库文档：上传受理后由异步管线切块向量化，状态机可观测 */
@Data
@TableName("kb_doc")
public class KbDoc {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 原始文件名（含扩展名） */
  private String fileName;

  /** 文件大小（字节） */
  private Long sizeBytes;

  /** 向量块数量（完成后回填） */
  private Integer chunkCount;

  /** 0=处理中 1=完成 2=失败 */
  private Integer status;

  /** 失败原因（可读中文，截断 500 字符） */
  private String errorMsg;

  /** 关联 kb_file.id（NULL=旧路径正文直接入库的 ≤10MB md/txt 文档） */
  private Long fileId;

  /** 处理阶段：PARSING/CHUNKING/EMBEDDING（完成置 NULL） */
  private String stage;

  /** 处理进度 0-100（完成 100） */
  private Integer progress;

  /** 已完成向量化的块数（embedding 断点续跑依据） */
  private Integer processedChunks;

  /** 切块总块数（向量化阶段进度计算与断点判定） */
  private Integer totalChunks;

  private Long uploaderId;

  private String uploaderName;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
