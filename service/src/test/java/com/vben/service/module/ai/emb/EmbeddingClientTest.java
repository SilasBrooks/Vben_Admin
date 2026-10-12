package com.vben.service.module.ai.emb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vben.service.module.ai.client.AiUpstreamException;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Embedding 客户端单测：双协议请求地址归一化、响应 JSON 解析
 * （标准数组形态 + 方舟单对象形态、乱序 index 排序）与失败分支。
 */
class EmbeddingClientTest {

  private final EmbeddingClient client = new EmbeddingClient();

  // ------------------------------------------------------------------
  // 地址归一化（标准协议）
  // ------------------------------------------------------------------

  @Test
  void bareDomainGetsV1Prefix() {
    assertThat(EmbeddingClient.embeddingsUri("https://api.siliconflow.cn", false))
        .isEqualTo(URI.create("https://api.siliconflow.cn/v1/embeddings"));
    assertThat(EmbeddingClient.embeddingsUri("https://api.openai.com", false))
        .isEqualTo(URI.create("https://api.openai.com/v1/embeddings"));
  }

  @Test
  void versionedPathAppendsActionOnly() {
    assertThat(EmbeddingClient.embeddingsUri("https://api.deepseek.com/v1", false))
        .isEqualTo(URI.create("https://api.deepseek.com/v1/embeddings"));
    // 火山方舟 /api/v3
    assertThat(EmbeddingClient.embeddingsUri("https://ark.cn-beijing.volces.com/api/v3", false))
        .isEqualTo(URI.create("https://ark.cn-beijing.volces.com/api/v3/embeddings"));
  }

  @Test
  void trailingSlashIsStripped() {
    assertThat(EmbeddingClient.embeddingsUri("https://api.deepseek.com/", false))
        .isEqualTo(URI.create("https://api.deepseek.com/v1/embeddings"));
    assertThat(EmbeddingClient.embeddingsUri("https://api.deepseek.com/v1/", false))
        .isEqualTo(URI.create("https://api.deepseek.com/v1/embeddings"));
  }

  @Test
  void fullPathsAreUsedAsIs() {
    assertThat(EmbeddingClient.embeddingsUri("https://example.com/api/v1/embeddings", false))
        .isEqualTo(URI.create("https://example.com/api/v1/embeddings"));
  }

  // ------------------------------------------------------------------
  // 地址归一化（方舟多模态协议）
  // ------------------------------------------------------------------

  @Test
  void arkMultimodalUriAppendsMultimodalSuffix() {
    assertThat(EmbeddingClient.embeddingsUri("https://ark.cn-beijing.volces.com/api/v3", true))
        .isEqualTo(URI.create("https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal"));
    assertThat(EmbeddingClient.embeddingsUri("https://ark.cn-beijing.volces.com/api/v3/", true))
        .isEqualTo(URI.create("https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal"));
  }

  @Test
  void arkMultimodalFullPathUsedAsIs() {
    assertThat(EmbeddingClient.embeddingsUri(
        "https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal", true))
        .isEqualTo(URI.create("https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal"));
  }

  @Test
  void arkMultimodalBareDomainGetsV1Prefix() {
    assertThat(EmbeddingClient.embeddingsUri("https://ark.example.com", true))
        .isEqualTo(URI.create("https://ark.example.com/v1/embeddings/multimodal"));
  }

  // ------------------------------------------------------------------
  // 无 Key 快速失败（不发 HTTP）
  // ------------------------------------------------------------------

  @Test
  void embedWithoutKeyFailsFast() {
    EmbEndpoint noKey = new EmbEndpoint("https://api.example.com", "  ", "bge-m3", null, 30);
    assertThatThrownBy(() -> client.embed(noKey, List.of("文本")))
        .isInstanceOf(AiUpstreamException.class)
        .hasMessageContaining("API Key");
  }

  // ------------------------------------------------------------------
  // 响应 JSON 解析（标准数组形态）
  // ------------------------------------------------------------------

  @Test
  void parsesVectorsSortedByIndex() {
    String body = """
        {"object":"list","data":[
          {"index":1,"embedding":[0.4,0.5,0.6]},
          {"index":0,"embedding":[0.1,0.2,0.3]}
        ]}
        """;
    List<float[]> vectors = client.parseVectors(body);
    assertThat(vectors).hasSize(2);
    assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
    assertThat(vectors.get(1)).containsExactly(0.4f, 0.5f, 0.6f);
  }

  // ------------------------------------------------------------------
  // 响应 JSON 解析（方舟多模态单对象形态）
  // ------------------------------------------------------------------

  @Test
  void parsesArkMultimodalObjectData() {
    String body = """
        {"created":1791597887,
         "data":{"embedding":[0.011779,-0.015869,0.011901]},
         "object":"embedding"}
        """;
    List<float[]> vectors = client.parseVectors(body);
    assertThat(vectors).hasSize(1);
    assertThat(vectors.get(0)).containsExactly(0.011779f, -0.015869f, 0.011901f);
  }

  @Test
  void rejectsMissingDataArray() {
    assertThatThrownBy(() -> client.parseVectors("{\"error\":{\"message\":\"x\"}}"))
        .isInstanceOf(AiUpstreamException.class)
        .hasMessageContaining("格式不符合");
  }

  @Test
  void rejectsMissingEmbedding() {
    assertThatThrownBy(() -> client.parseVectors("{\"data\":[{\"index\":0}]}"))
        .isInstanceOf(AiUpstreamException.class)
        .hasMessageContaining("缺少向量数据");
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> client.parseVectors("not-json"))
        .isInstanceOf(AiUpstreamException.class)
        .hasMessageContaining("解析失败");
  }

  // ------------------------------------------------------------------
  // 协议判定
  // ------------------------------------------------------------------

  @Test
  void nullProtocolFallsBackToStandard() {
    EmbEndpoint endpoint = new EmbEndpoint("https://api.example.com", "k", "m", null, 30);
    assertThat(endpoint.arkMultimodal()).isFalse();
    EmbEndpoint ark = new EmbEndpoint("https://ark.example.com", "k", "m",
        SysEmbeddingConfig.PROTOCOL_ARK_MULTIMODAL, 30);
    assertThat(ark.arkMultimodal()).isTrue();
  }
}
