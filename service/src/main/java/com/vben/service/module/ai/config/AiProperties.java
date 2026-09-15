package com.vben.service.module.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepSeek 大模型配置（AI 智能助手）。
 *
 * <p>api-key 只允许存在于后端：dev 配置写入本地值并支持 {@code DEEPSEEK_API_KEY}
 * 环境变量覆盖；生产环境必须通过环境变量注入，前端任何接口都不会返回该值。
 */
@Data
@ConfigurationProperties(prefix = "deepseek")
public class AiProperties {

  /** DeepSeek 接口根地址（不带末尾斜杠） */
  private String baseUrl = "https://api.deepseek.com";

  /** API Key；为空时 AI 接口返回"服务未配置" */
  private String apiKey = "";

  /** 模型名：deepseek-chat（V3，支持 function calling 与流式） */
  private String model = "deepseek-chat";

  /** 单次上游请求超时（秒） */
  private int timeoutSeconds = 60;

  /** 单次对话内查询类工具自动执行的最大轮数，超出后让模型基于已有信息作答 */
  private int maxToolRounds = 3;
}
