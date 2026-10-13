package com.vben.service.module.ai.rerank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vben.service.module.ai.client.AiUpstreamException;
import org.junit.jupiter.api.Test;

/**
 * Rerank 客户端单测：响应解析（relevance_score 降序兜底）与请求地址归一化。
 */
class RerankClientTest {

  private final RerankClient client = new RerankClient();

  @Test
  void parseResultsSortsByRelevanceDesc() {
    String body = """
        {"id":"x","results":[
          {"index":1,"relevance_score":0.31},
          {"index":0,"relevance_score":0.95},
          {"index":2,"relevance_score":0.60}
        ]}
        """;

    var hits = client.parseResults(body);

    // 上游排序不保证：按 relevance_score 降序自行兜底
    assertThat(hits).hasSize(3);
    assertThat(hits.get(0).index()).isZero();
    assertThat(hits.get(0).relevanceScore()).isEqualTo(0.95);
    assertThat(hits.get(1).index()).isEqualTo(2);
    assertThat(hits.get(2).index()).isEqualTo(1);
  }

  @Test
  void parseResultsRejectsMissingResults() {
    assertThatThrownBy(() -> client.parseResults("{\"data\":[]}"))
        .isInstanceOf(AiUpstreamException.class);
  }

  @Test
  void rerankUriNormalization() {
    // 裸域名 → 补 /v1 前缀
    assertThat(RerankClient.rerankUri("https://api.example.com").toString())
        .isEqualTo("https://api.example.com/v1/rerank");
    // 已带版本段 → 直接追加
    assertThat(RerankClient.rerankUri("https://api.example.com/v1").toString())
        .isEqualTo("https://api.example.com/v1/rerank");
    // 已写全 → 原样（末尾斜杠归一化）
    assertThat(RerankClient.rerankUri("https://api.example.com/v1/rerank/").toString())
        .isEqualTo("https://api.example.com/v1/rerank");
  }
}
