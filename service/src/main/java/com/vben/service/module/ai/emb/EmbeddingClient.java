package com.vben.service.module.ai.emb;

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
 * Embedding 向量化客户端（非流式，双协议）。
 *
 * <p>协议风格由配置决定（{@code EmbEndpoint.protocol}）：
 * <ul>
 *   <li>standard：OpenAI 兼容 {@code /embeddings}，input 为纯文本数组</li>
 *   <li>ark-multimodal：方舟 {@code /embeddings/multimodal}（vision 系列），
 *       文本包装为 {@code [{"type":"text","text":...}]}</li>
 * </ul>
 * 响应统一按 {@code data} 解析（兼容数组与单对象两种形态）。
 *
 * <p>使用 JDK 内置 HttpClient，零新增依赖；输入文本列表，内部按批
 * （{@link #BATCH_SIZE}，常见单批上限内）拆分调用后按序合并。
 * Key 只在本类随请求头发送；任何失败都以 {@link AiUpstreamException} 抛出可读中文原因。
 */
@Slf4j
@Component
public class EmbeddingClient {

  /** 单批最大文本条数（上游单请求批量上限的保守值） */
  public static final int BATCH_SIZE = 16;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final HttpClient httpClient;

  public EmbeddingClient() {
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
  }

  /** 标准协议请求体（input 为文本数组） */
  private record EmbedRequest(String model, List<String> input) {
  }

  /** 方舟多模态请求体（input 为结构化条目，知识库场景仅 text 类型） */
  private record ArkMultimodalRequest(String model, List<TextItem> input) {

    private record TextItem(String type, String text) {
    }
  }

  /** 单批向量化结果：向量列表与维度 */
  public record EmbedResult(List<float[]> vectors, int dimension) {
  }

  /**
   * 批量向量化：自动拆分调用，返回顺序与输入一致。
   * 方舟多模态协议的 input 语义是「多个部件合成一个向量」，
   * 必须每条文本独立请求（批大小强制 1）；标准协议按 {@link #BATCH_SIZE} 拆批。
   */
  public EmbedResult embed(EmbEndpoint endpoint, List<String> texts) {
    if (!endpoint.hasKey()) {
      throw new AiUpstreamException("该 Embedding 配置未填写 API Key");
    }
    int batchSize = endpoint.arkMultimodal() ? 1 : BATCH_SIZE;
    List<float[]> all = new ArrayList<>(texts.size());
    int dimension = -1;
    for (int from = 0; from < texts.size(); from += batchSize) {
      List<String> batch = texts.subList(from, Math.min(from + batchSize, texts.size()));
      List<float[]> vectors = embedBatch(endpoint, batch);
      for (float[] v : vectors) {
        if (dimension == -1) {
          dimension = v.length;
        } else if (v.length != dimension) {
          throw new AiUpstreamException("上游返回向量维度不一致，请检查模型配置");
        }
      }
      all.addAll(vectors);
    }
    return new EmbedResult(all, dimension);
  }

  /** 单条便捷方法（连通测试/检索用），返回首条向量 */
  public float[] embedOne(EmbEndpoint endpoint, String text) {
    return embed(endpoint, List.of(text)).vectors().get(0);
  }

  /** 单批请求：按协议风格构造请求体与路径，标准协议按 index 排序合并 */
  private List<float[]> embedBatch(EmbEndpoint endpoint, List<String> texts) {
    Object body = endpoint.arkMultimodal()
        ? new ArkMultimodalRequest(endpoint.model(),
            texts.stream().map(t -> new ArkMultimodalRequest.TextItem("text", t)).toList())
        : new EmbedRequest(endpoint.model(), texts);
    String requestBody;
    try {
      requestBody = MAPPER.writeValueAsString(body);
    } catch (Exception e) {
      throw new AiUpstreamException("Embedding 请求序列化失败", e);
    }

    URI uri = embeddingsUri(endpoint.baseUrl(), endpoint.arkMultimodal());
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
      throw new AiUpstreamException("Embedding 服务响应超时，请稍后重试", e);
    } catch (Exception e) {
      throw new AiUpstreamException("无法连接到 Embedding 接口地址，请检查地址与网络可达性", e);
    }

    if (response.statusCode() != 200) {
      throw new AiUpstreamException(mapHttpError(response.statusCode(), response.body(), uri));
    }
    return parseVectors(response.body());
  }

  /**
   * 解析向量响应（包可见便于单测）：兼容两种形态——
   * standard 的 {@code data:[{index,embedding}...]} 数组（按 index 排序），
   * ark-multimodal 的 {@code data:{embedding:[...]}} 单对象。
   */
  List<float[]> parseVectors(String body) {
    try {
      JsonNode root = MAPPER.readTree(body);
      JsonNode data = root.path("data");
      if (data.isObject()) {
        return List.of(readVector(data));
      }
      if (!data.isArray() || data.isEmpty()) {
        throw new AiUpstreamException("Embedding 响应格式不符合所选协议");
      }
      List<JsonNode> items = new ArrayList<>();
      data.forEach(items::add);
      items.sort((a, b) -> Integer.compare(a.path("index").asInt(0), b.path("index").asInt(0)));
      List<float[]> vectors = new ArrayList<>(items.size());
      for (JsonNode item : items) {
        vectors.add(readVector(item));
      }
      return vectors;
    } catch (AiUpstreamException e) {
      throw e;
    } catch (Exception e) {
      throw new AiUpstreamException("Embedding 响应解析失败", e);
    }
  }

  private float[] readVector(JsonNode item) {
    JsonNode arr = item.path("embedding");
    if (!arr.isArray() || arr.isEmpty()) {
      throw new AiUpstreamException("Embedding 响应缺少向量数据");
    }
    float[] vec = new float[arr.size()];
    for (int i = 0; i < arr.size(); i++) {
      vec[i] = (float) arr.get(i).asDouble();
    }
    return vec;
  }

  /** HTTP 状态码 → 中文可读提示（404 携带实际请求路径，协议配错可一眼定位） */
  private String mapHttpError(int status, String body, URI uri) {
    log.warn("Embedding 上游错误 status={} uri={} body={}", status, uri, body);
    return switch (status) {
      case 401 -> "Embedding 服务 API Key 无效或已过期，请在嵌入配置中检查该配置的 Key";
      case 402, 403 -> "Embedding 服务账户余额不足或无调用权限，请检查该配置对应账户";
      case 404 -> "Embedding 模型或接口地址配置错误（请求路径 " + uri.getPath()
          + "），请检查 base_url、model 与协议风格是否匹配";
      case 429 -> "Embedding 请求过于频繁，请稍后再试";
      default -> "Embedding 服务返回错误（HTTP " + status + "），请稍后重试";
    };
  }

  /**
   * 拼接向量化请求地址（归一化规则与对话客户端一致）：
   * 已带版本路径段（/v1 等）→ 追加端点；已写全 → 原样；裸域名 → 补 /v1 前缀。
   * 方舟多模态协议追加 {@code /embeddings/multimodal}，标准协议追加 {@code /embeddings}。
   */
  static URI embeddingsUri(String baseUrl, boolean arkMultimodal) {
    String suffix = arkMultimodal ? "/embeddings/multimodal" : "/embeddings";
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
}
