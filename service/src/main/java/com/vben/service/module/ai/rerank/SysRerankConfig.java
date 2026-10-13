package com.vben.service.module.ai.rerank;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Rerank 精排模型配置：知识库检索「召回 → RRF 融合 → 精排」第三层使用。
 *
 * <p>协议：OpenAI 兼容 {@code POST {base}/rerank}（硅基流动/Jina 等通用形态）。
 * 与 {@link com.vben.service.module.ai.emb.SysEmbeddingConfig} 同安全模式：
 * api_key 明文入库（内网约定），回显前必须经 {@link SysRerankConfigService#maskKey} 脱敏；
 * is_active 全局唯一，激活由事务保证。无 yml 兜底：未配置即精排层关闭。
 */
@Data
@TableName("sys_rerank_config")
public class SysRerankConfig {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 显示名（唯一） */
  private String name;

  /** 接口根地址（不带末尾斜杠），如 https://api.siliconflow.cn/v1 */
  private String baseUrl;

  /** API Key（明文入库，回显脱敏；空串=未配置） */
  private String apiKey;

  /** 模型名，如 BAAI/bge-reranker-v2-m3 */
  private String model;

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
