package com.vben.service.module.ai.llm;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LLM 模型配置：OpenAI 兼容协议的对话模型接入点，AI 助手底层模型由此动态决定。
 *
 * <p>api_key 明文入库（内网约定），任何接口回显前必须经
 * {@link SysLlmConfigService#maskKey} 脱敏；is_active 全局唯一，切换由 activate 事务保证。
 */
@Data
@TableName("sys_llm_config")
public class SysLlmConfig {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 显示名（唯一） */
  private String name;

  /** 接口根地址（OpenAI 兼容，不带末尾斜杠），如 https://api.deepseek.com */
  private String baseUrl;

  /** API Key（明文入库，回显脱敏；空串=未配置） */
  private String apiKey;

  /** 模型名，如 deepseek-chat / qwen-plus */
  private String model;

  /** 采样温度，NULL=不下发由上游默认 */
  private BigDecimal temperature;

  /** 最大生成 token 数，NULL=不下发 */
  private Integer maxTokens;

  /** 单次上游请求超时（秒） */
  private Integer timeoutSeconds;

  /** 1 启用 0 停用（停用不可激活） */
  private Integer enabled;

  /** 1=当前激活模型（全局唯一） */
  private Integer isActive;

  /** 备注 */
  private String remark;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
