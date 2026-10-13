package com.vben.service.module.ai.kb.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 检索质量评测集：query + 期望命中文档的用例分组 */
@Data
@TableName("sys_kb_eval_set")
public class KbEvalSet {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 评测集名称（唯一） */
  private String name;

  /** 描述 */
  private String description;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
