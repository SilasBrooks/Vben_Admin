package com.vben.service.module.ai.kb.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 检索评测用例：查询文本 + 期望命中文档（可选期望块序号）。
 *
 * <p>expect_doc_id 创建/编辑时校验存在于 kb_doc；文档删除不回溯清理——
 * 运行时该用例自然未命中（docId 无法匹配），快照语义下历史运行不受影响。
 */
@Data
@TableName("sys_kb_eval_case")
public class KbEvalCase {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属评测集 id（集合删除级联） */
  private Long setId;

  /** 查询文本 */
  private String query;

  /** 期望命中的 kb_doc.id */
  private Long expectDocId;

  /** 期望块序号（NULL=不限定块序，仅按 docId 判定命中） */
  private Integer expectSeq;

  /** 备注 */
  private String note;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
