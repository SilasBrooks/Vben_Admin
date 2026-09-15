package com.vben.service.module.ai.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.module.ai.config.AiProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 流式对话客户端（OpenAI 兼容协议）。
 *
 * <p>使用 JDK 内置 HttpClient，零新增依赖；Key 只在本类随请求头发送，不向外暴露。
 * SSE 分片在此层解析并聚合：content 逐字回调，tool_calls 按 index 拼齐
 * arguments 分片后整体回调，上层不感知流式分片细节。
 */
@Slf4j
@Component
public class DeepSeekClient {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String SSE_DATA_PREFIX = "data:";
  private static final String SSE_DONE = "[DONE]";

  private final AiProperties properties;
  private final HttpClient httpClient;

  public DeepSeekClient(AiProperties properties) {
    this.properties = properties;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  /** 上游请求体（tools 为空时不下发该字段） */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private record ChatRequest(String model, List<DeepMessage> messages,
                             Boolean stream, List<Map<String, Object>> tools) {
  }

  /**
   * 发起一轮流式对话。
   *
   * @param messages  完整对话历史（调用方负责维护）
   * @param tools     本轮可用工具定义（OpenAI tools schema），空列表表示纯对话
   * @param handler   文本/工具回调
   * @param cancel    取消令牌
   */
  public void streamChat(List<DeepMessage> messages, List<Map<String, Object>> tools,
                         StreamHandler handler, CancelToken cancel) {
    if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
      throw new AiUpstreamException("AI 服务未配置 API Key，请联系管理员设置 DEEPSEEK_API_KEY");
    }

    String requestBody;
    try {
      requestBody = MAPPER.writeValueAsString(new ChatRequest(
          properties.getModel(), messages, Boolean.TRUE, tools));
    } catch (Exception e) {
      throw new AiUpstreamException("AI 请求序列化失败", e);
    }

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(properties.getBaseUrl() + "/v1/chat/completions"))
        .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + properties.getApiKey())
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build();

    HttpResponse<java.util.stream.Stream<String>> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
    } catch (HttpTimeoutException e) {
      throw new AiUpstreamException("AI 服务响应超时，请稍后重试", e);
    } catch (Exception e) {
      if (cancel.isCancelled() || Thread.currentThread().isInterrupted()) {
        throw new AiStreamCancelledException();
      }
      throw new AiUpstreamException("AI 服务暂时不可用，请稍后重试", e);
    }

    if (response.statusCode() != 200) {
      String errorBody;
      try (var lines = response.body()) {
        errorBody = lines.reduce("", (a, b) -> a + b);
      }
      throw new AiUpstreamException(mapHttpError(response.statusCode(), errorBody));
    }

    parseSse(response, handler, cancel);
  }

  /** 逐行解析 SSE：聚合 content 与 tool_calls 分片 */
  private void parseSse(HttpResponse<java.util.stream.Stream<String>> response,
                        StreamHandler handler, CancelToken cancel) {
    // index -> 聚合中的工具调用
    Map<Integer, ToolCallAggregator> aggregators = new LinkedHashMap<>();

    try (var lines = response.body()) {
      lines.forEach(line -> {
        if (cancel.isCancelled() || Thread.currentThread().isInterrupted()) {
          throw new AiStreamCancelledException();
        }
        if (line == null || line.isBlank() || !line.startsWith(SSE_DATA_PREFIX)) {
          return;
        }
        String payload = line.substring(SSE_DATA_PREFIX.length()).trim();
        if (SSE_DONE.equals(payload)) {
          return;
        }
        try {
          JsonNode chunk = MAPPER.readTree(payload);
          JsonNode choice = chunk.path("choices").path(0);
          if (choice.isMissingNode()) {
            return;
          }
          JsonNode delta = choice.path("delta");

          // 正文片段
          String text = delta.path("content").asText(null);
          if (text != null && !text.isEmpty()) {
            handler.onText(text);
          }

          // 工具调用分片（id/name 通常首片出现，arguments 逐片追加）
          JsonNode toolCallsNode = delta.path("tool_calls");
          if (toolCallsNode.isArray()) {
            for (JsonNode node : toolCallsNode) {
              int index = node.path("index").asInt(0);
              ToolCallAggregator agg = aggregators.computeIfAbsent(index, k -> new ToolCallAggregator());
              if (node.hasNonNull("id")) {
                agg.id = node.get("id").asText();
              }
              JsonNode fn = node.path("function");
              if (!fn.isMissingNode()) {
                if (fn.hasNonNull("name")) {
                  agg.name = fn.get("name").asText();
                }
                if (fn.hasNonNull("arguments")) {
                  agg.arguments.append(fn.get("arguments").asText());
                }
              }
            }
          }

          // 本轮以工具调用结束：拼齐后整体回调
          if ("tool_calls".equals(choice.path("finish_reason").asText(null))) {
            List<ToolCall> toolCalls = new ArrayList<>();
            for (ToolCallAggregator agg : aggregators.values()) {
              toolCalls.add(ToolCall.of(agg.id, agg.name, agg.arguments.toString()));
            }
            handler.onToolCalls(toolCalls);
          }
        } catch (AiStreamCancelledException e) {
          throw e;
        } catch (Exception e) {
          log.warn("DeepSeek SSE 分片解析失败，已跳过: {}", payload, e);
        }
      });
    } catch (AiStreamCancelledException e) {
      throw e;
    } catch (Exception e) {
      if (cancel.isCancelled() || Thread.currentThread().isInterrupted()) {
        throw new AiStreamCancelledException();
      }
      if (e instanceof AiUpstreamException aue) {
        throw aue;
      }
      throw new AiUpstreamException("读取 AI 流式响应失败，请稍后重试", e);
    }
  }

  /** HTTP 状态码 → 中文可读提示 */
  private String mapHttpError(int status, String body) {
    log.warn("DeepSeek 上游错误 status={} body={}", status, body);
    return switch (status) {
      case 401 -> "DeepSeek API Key 无效或已过期，请联系管理员检查配置";
      case 402, 403 -> "DeepSeek 账户余额不足或无调用权限，请充值后再试";
      case 404 -> "DeepSeek 模型或接口地址配置错误";
      case 429 -> "AI 请求过于频繁，请稍后再试";
      default -> "AI 服务返回错误（HTTP " + status + "），请稍后重试";
    };
  }

  /** 工具调用分片聚合器 */
  private static class ToolCallAggregator {
    private String id;
    private String name;
    private final StringBuilder arguments = new StringBuilder();
  }
}
