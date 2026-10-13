package com.vben.service.module.ai.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.module.ai.client.AiUpstreamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Rerank 精排客户端：OpenAI 兼容 {@code POST {base}/rerank}（硅基流动/Jina 通用形态）。
 *
 * <p>请求 {@code {model, query, documents:[], top_n}}，响应
 * {@code {results:[{index, relevance_score}]}}；按相关性得分降序返回
 * （上游排序不保证时自行排序兜底）。使用 JDK 内置 HttpClient，零新增依赖；
 * Key 只在本类随请求头发送。
 *
 * <p>失败语义分两档：检索链路用 {@link #rerankQuiet}（失败仅告警返回 null，
 * 调用方降级 RRF 结果，AI 问答不中断）；连通测试用 {@link #rerank}（失败抛出
 * 可读原因，管理员需要看到具体错误）。
 */
@Slf4j
@Component
public class RerankClient {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final HttpClient httpClient;

  public RerankClient() {
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  /** 单条精排结果：候选下标与相关性得分（越大越相关） */
  public record RerankHit(int index, double relevanceScore) {
  }

  /**
   * 静默精排（检索链路专用）：任何失败仅记录告警并返回 null，
   * 调用方降级为 RRF 融合顺序，保证检索与 AI 问答不因精排故障中断。
   */
  public List<RerankHit> rerankQuiet(RerankEndpoint endpoint, String query,
                                     List<String> documents, int topN) {
    try {
      return rerank(endpoint, query, documents, topN);
    } catch (Exception e) {
      log.warn("Rerank 精排失败，降级为 RRF 融合顺序：{}", e.getMessage());
      return null;
    }
  }

  /** 精排：失败抛 AiUpstreamException（连通测试等需要看到具体错误的场景） */
  public List<RerankHit> rerank(RerankEndpoint endpoint, String query,
                                List<String> documents, int topN) {
    if (!endpoint.hasKey()) {
      throw new AiUpstreamException("该 Rerank 配置未填写 API Key");
    }
    String requestBody;
    try {
      requestBody = MAPPER.writeValueAsString(new RerankRequest(
          endpoint.model(), query, documents, topN));
    } catch (Exception e) {
      throw new AiUpstreamException("Rerank 请求序列化失败", e);
    }

    URI uri = rerankUri(endpoint.baseUrl());
    HttpRequest request = HttpRequest.newBuilder()
        .uri(uri)
        .timeout(Duration.ofSeconds(endpoint.timeoutSeconds()))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + endpoint.apiKey())
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build();

    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (java.net.http.HttpTimeoutException e) {
      throw new AiUpstreamException("Rerank 服务响应超时，请稍后重试", e);
    } catch (Exception e) {
      throw new AiUpstreamException("无法连接到 Rerank 接口地址，请检查地址与网络可达性", e);
    }
    if (response.statusCode() != 200) {
      throw new AiUpstreamException(mapHttpError(response.statusCode(), response.body(), uri));
    }
    return parseResults(response.body());
  }

  /**
   * 解析精排响应（包可见便于单测）：{@code results:[{index, relevance_score}]}，
   * 按相关性得分降序返回（上游排序不保证时自行排序兜底）。
   */
  List<RerankHit> parseResults(String body) {
    try {
      JsonNode results = MAPPER.readTree(body).path("results");
      if (!results.isArray() || results.isEmpty()) {
        throw new AiUpstreamException("Rerank 响应缺少 results 数据");
      }
      List<RerankHit> hits = new ArrayList<>(results.size());
      for (JsonNode item : results) {
        hits.add(new RerankHit(item.path("index").asInt(-1),
            item.path("relevance_score").asDouble(0.0)));
      }
      hits.sort((a, b) -> Double.compare(b.relevanceScore(), a.relevanceScore()));
      return hits;
    } catch (AiUpstreamException e) {
      throw e;
    } catch (Exception e) {
      throw new AiUpstreamException("Rerank 响应解析失败", e);
    }
  }

  /** HTTP 状态码 → 中文可读提示（404 携带实际请求路径，地址配错可一眼定位） */
  private String mapHttpError(int status, String body, URI uri) {
    log.warn("Rerank 上游错误 status={} uri={} body={}", status, uri, body);
    return switch (status) {
      case 401 -> "Rerank 服务 API Key 无效或已过期，请在精排配置中检查该配置的 Key";
      case 402, 403 -> "Rerank 服务账户余额不足或无调用权限，请检查该配置对应账户";
      case 404 -> "Rerank 模型或接口地址配置错误（请求路径 " + uri.getPath()
          + "），请检查 base_url 与 model 是否匹配";
      case 429 -> "Rerank 请求过于频繁，请稍后再试";
      default -> "Rerank 服务返回错误（HTTP " + status + "），请稍后重试";
    };
  }

  /**
   * 拼接精排请求地址（归一化规则与对话/向量客户端一致）：
   * 已带版本路径段（/v1 等）→ 追加 /rerank；已写全 → 原样；裸域名 → 补 /v1 前缀。
   */
  static URI rerankUri(String baseUrl) {
    String suffix = "/rerank";
    String root = baseUrl == null ? "" : baseUrl.trim();
    while (root.endsWith("/")) {
      root = root.substring(0, root.length() - 1);
    }
    if (root.endsWith(suffix)) {
      return URI.create(root);
    }
    if (root.matches(".*" + "/v\\d+$")) {
      return URI.create(root + suffix);
    }
    return URI.create(root + "/v1" + suffix);
  }

  /** 精排请求体 */
  private record RerankRequest(String model, String query, List<String> documents, int top_n) {
  }
}
