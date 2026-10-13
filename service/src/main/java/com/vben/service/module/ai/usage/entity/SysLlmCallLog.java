package com.vben.service.module.ai.usage.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * LLM 调用用量日志：每次业务上游请求一条（chat 对话 / summarize 历史压缩），
 * 仪表盘 AI 用量数据源。仅记成功调用；tokens 为 NULL 表示上游未返回 usage
 * （调用次数仍准确，token 合计偏保守不虚报）。
 */
@Data
@TableName("sys_llm_call_log")
public class SysLlmCallLog {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 调用场景：chat=助手对话，summarize=历史压缩 */
  private String scene;

  /** 模型名快照（当次激活配置的模型） */
  private String model;

  /** 输入 token 数（NULL=上游未返回 usage） */
  private Long promptTokens;

  /** 输出 token 数（NULL=上游未返回 usage） */
  private Long completionTokens;

  /** 总 token 数（NULL=上游未返回 usage） */
  private Long totalTokens;

  /** 该上游请求耗时（毫秒） */
  private Long elapsedMs;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;
}
