package com.vben.service.module.ai.emb;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Embedding 模型配置：向量化接入点，知识库切块向量化与检索使用。
 *
 * <p>协议风格两种：standard = OpenAI 兼容 /embeddings（默认）；
 * ark-multimodal = 方舟 /embeddings/multimodal（vision 系列专用，文本包装为结构化入参）。
 *
 * <p>与 {@link com.vben.service.module.ai.llm.SysLlmConfig} 同安全模式：
 * api_key 明文入库（内网约定），回显前必须经 {@link SysEmbeddingConfigService#maskKey} 脱敏；
 * is_active 全局唯一，激活由事务保证。
 */
@Data
@TableName("sys_embedding_config")
public class SysEmbeddingConfig {

  /** 协议风格：标准 OpenAI 兼容 */
  public static final String PROTOCOL_STANDARD = "standard";

  /** 协议风格：方舟多模态（vision 系列） */
  public static final String PROTOCOL_ARK_MULTIMODAL = "ark-multimodal";

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 显示名（唯一） */
  private String name;

  /** 接口根地址（不带末尾斜杠），如 https://ark.cn-beijing.volces.com/api/v3 */
  private String baseUrl;

  /** API Key（明文入库，回显脱敏；空串=未配置） */
  private String apiKey;

  /** 模型名，如 bge-m3 / doubao-embedding-vision-251215 */
  private String model;

  /** 协议风格：standard / ark-multimodal（NULL 视为 standard，存量行兼容） */
  private String protocol;

  /** 向量维度（连通测试实测回填，NULL=未知） */
  private Integer dimension;

  /** 单次上游请求超时（秒） */
  private Integer timeoutSeconds;

  /** 1 启用 0 停用（停用不可激活） */
  private Integer enabled;

  /** 1=当前激活配置（全局唯一） */
  private Integer isActive;

  /** 备注 */
  private String remark;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
