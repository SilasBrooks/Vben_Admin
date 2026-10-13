package com.vben.service.module.ai.kb.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检索评测运行记录（快照语义）：指标、精排/Embedding 状态快照与逐用例明细。
 *
 * <p>后续编辑用例或文档 MUST NOT 影响已产生的历史记录；
 * detail 为逐用例结果 JSON 数组文本（query/expectDocId/expectSeq/rank/topHits）。
 */
@Data
@TableName("sys_kb_eval_run")
public class KbEvalRun {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 所属评测集 id（集合删除级联） */
  private Long setId;

  /** 评测集名称快照（集合改名不影响历史） */
  private String setName;

  /** 检索 Top-K（固定 5） */
  private Integer topK;

  /** 总用例数 */
  private Integer totalCases;

  /** 期望命中进入前 1 名的用例占比（列名含双下划线数字，需显式映射） */
  @TableField("hit_at_1")
  private BigDecimal hitAt1;

  /** 期望命中进入前 3 名的用例占比 */
  @TableField("hit_at_3")
  private BigDecimal hitAt3;

  /** 期望命中进入前 5 名的用例占比 */
  @TableField("hit_at_5")
  private BigDecimal hitAt5;

  /** MRR：命中排名倒数之和 / 用例数（未命中计 0） */
  private BigDecimal mrr;

  /** 执行耗时（毫秒） */
  private Long elapsedMs;

  /** 执行时是否存在激活 Rerank 配置（1=有，精排参与排序） */
  private Integer rerankActive;

  /** 执行时激活的 Embedding 模型名快照 */
  private String embeddingModel;

  /** 逐用例结果 JSON 数组文本 */
  private String detail;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}
