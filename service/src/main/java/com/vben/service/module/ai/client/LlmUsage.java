package com.vben.service.module.ai.client;

/**
 * 单次上游 LLM 请求的用量快照（OpenAI 兼容协议 usage 字段）。
 *
 * @param model            模型名（当次激活配置的模型）
 * @param promptTokens     输入 token 数（null=上游未返回 usage）
 * @param completionTokens 输出 token 数（null=上游未返回 usage）
 * @param totalTokens      总 token 数（null=上游未返回 usage）
 * @param elapsedMs        该请求耗时（毫秒）
 */
public record LlmUsage(String model, Long promptTokens, Long completionTokens,
                       Long totalTokens, long elapsedMs) {
}
