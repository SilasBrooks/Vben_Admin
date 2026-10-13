package com.vben.service.module.ai.kb;

import lombok.Data;

/** 混合检索命中项（MyBatis 结果映射 POJO） */
@Data
public class KbSearchHit {

  private Long id;

  private Long docId;

  private Integer seq;

  private String content;

  private String fileName;

  /** 融合得分：向量与全文两路 RRF（1/(60+rank) 双份），越大越相关，排序依据 */
  private Double score;

  /** 向量通道得分 = 1 - 余弦距离，越大越相似（退化路径与融合路径均有值） */
  private Double vecScore;

  /** 匹配通道：vector 仅向量命中 / fts 仅全文命中 / both 双通道命中 */
  private String matchType;

  /** 精排得分（Rerank 模型 relevance_score，越大越相关）；null=未精排（未配置或降级） */
  private Double rerankScore;
}
