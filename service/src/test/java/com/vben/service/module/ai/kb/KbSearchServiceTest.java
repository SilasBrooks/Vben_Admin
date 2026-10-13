package com.vben.service.module.ai.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vben.service.common.BizException;
import com.vben.service.module.ai.emb.ActiveEmbeddingResolver;
import com.vben.service.module.ai.emb.EmbEndpoint;
import com.vben.service.module.ai.emb.EmbeddingClient;
import com.vben.service.module.ai.kb.mapper.KbChunkMapper;
import com.vben.service.module.ai.rerank.ActiveRerankResolver;
import com.vben.service.module.ai.rerank.RerankClient;
import com.vben.service.module.ai.rerank.RerankEndpoint;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 知识库混合检索服务单测：向量字面量格式、Top-K 收敛（1-10）、空库短路与维度一致性校验、
 * 纯符号查询退化纯向量路径、精排层（放宽召回/重排取 Top-K/降级 RRF）。
 */
class KbSearchServiceTest {

  private final KbChunkMapper chunkMapper = Mockito.mock(KbChunkMapper.class);
  private final ActiveEmbeddingResolver resolver = Mockito.mock(ActiveEmbeddingResolver.class);
  private final EmbeddingClient embeddingClient = Mockito.mock(EmbeddingClient.class);
  private final ActiveRerankResolver rerankResolver = Mockito.mock(ActiveRerankResolver.class);
  private final RerankClient rerankClient = Mockito.mock(RerankClient.class);
  private final KbSearchService service =
      new KbSearchService(chunkMapper, resolver, embeddingClient, rerankResolver, rerankClient);

  private void stubNonEmptyBase() {
    when(chunkMapper.countAll()).thenReturn(5L);
    when(resolver.requireActive())
        .thenReturn(new EmbEndpoint("https://api.example.com", "sk-x", "bge-m3", null, 30));
    when(embeddingClient.embedOne(any(), anyString())).thenReturn(new float[] {0.1f});
    when(chunkMapper.distinctDimensions()).thenReturn(List.of(1));
    when(chunkMapper.hybridSearch(anyString(), anyString(), Mockito.anyInt()))
        .thenReturn(List.of());
    when(rerankResolver.resolveOrNull()).thenReturn(null);
  }

  /** 构造 n 条融合候选（content 与 seq 递增可区分） */
  private List<KbSearchHit> candidates(int n) {
    List<KbSearchHit> hits = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      KbSearchHit hit = new KbSearchHit();
      hit.setSeq(i);
      hit.setContent("候选" + i);
      hit.setScore(1.0 / (60 + i + 1));
      hits.add(hit);
    }
    return hits;
  }

  @Test
  void toVectorStringFormatsPgvectorLiteral() {
    assertThat(KbSearchService.toVectorString(new float[] {0.1f, 0.25f, -3f}))
        .isEqualTo("[0.1,0.25,-3.0]");
  }

  @Test
  void topKIsClampedToOneToTen() {
    stubNonEmptyBase();

    service.search("问题", 0);
    verify(chunkMapper).hybridSearch(anyString(), anyString(), eq(1));

    service.search("问题", 99);
    verify(chunkMapper).hybridSearch(anyString(), anyString(), eq(10));
  }

  @Test
  void symbolOnlyQueryFallsBackToVectorSearch() {
    stubNonEmptyBase();
    when(chunkMapper.vectorSearch(anyString(), Mockito.anyInt())).thenReturn(List.of());

    service.search("!!!", 4);
    verify(chunkMapper).vectorSearch(anyString(), eq(4));
    verify(chunkMapper, never()).hybridSearch(anyString(), anyString(), Mockito.anyInt());
  }

  @Test
  void emptyKnowledgeBaseShortCircuits() {
    when(chunkMapper.countAll()).thenReturn(0L);

    assertThat(service.search("问题", 4)).isEmpty();
    verify(resolver, never()).requireActive();
    verify(embeddingClient, never()).embedOne(any(), anyString());
  }

  @Test
  void dimensionMismatchRejectsQuery() {
    stubNonEmptyBase();
    when(embeddingClient.embedOne(any(), anyString())).thenReturn(new float[1024]);
    when(chunkMapper.distinctDimensions()).thenReturn(List.of(768));

    // 混选型期间：查询维度与库中维度不一致直接报错，而非返回错配的相似度
    assertThatThrownBy(() -> service.search("问题", 4)).isInstanceOf(BizException.class);
  }

  @Test
  void noRerankKeepsRrfOrderAndNarrowRecall() {
    stubNonEmptyBase();
    when(chunkMapper.hybridSearch(anyString(), anyString(), Mockito.anyInt()))
        .thenReturn(candidates(8));

    List<KbSearchHit> hits = service.search("问题", 4);

    // 未配置精排：召回宽度即 Top-K，不触发精排，按 RRF 顺序截断
    verify(chunkMapper).hybridSearch(anyString(), anyString(), eq(4));
    verify(rerankClient, never()).rerankQuiet(any(), anyString(), any(), Mockito.anyInt());
    assertThat(hits).hasSize(4);
    assertThat(hits.get(0).getSeq()).isEqualTo(0);
  }

  @Test
  void activeRerankWidensRecallAndReorders() {
    stubNonEmptyBase();
    when(rerankResolver.resolveOrNull())
        .thenReturn(new RerankEndpoint(1L, "https://api.example.com", "sk-x", "bge-m3", 30));
    // 16 条候选（topK=4 时召回放宽下限），融合排名第 5 的候选精排得分最高
    when(chunkMapper.hybridSearch(anyString(), anyString(), Mockito.anyInt()))
        .thenReturn(candidates(16));
    when(rerankClient.rerankQuiet(any(), anyString(), any(), Mockito.anyInt()))
        .thenReturn(List.of(new RerankClient.RerankHit(4, 0.98),
            new RerankClient.RerankHit(0, 0.42),
            new RerankClient.RerankHit(1, 0.31),
            new RerankClient.RerankHit(2, 0.20)));

    List<KbSearchHit> hits = service.search("问题", 4);

    // 召回放宽为 max(4*4, 16)=16，精排后按相关性降序取 Top-K
    verify(chunkMapper).hybridSearch(anyString(), anyString(), eq(16));
    assertThat(hits).hasSize(4);
    assertThat(hits.get(0).getSeq()).isEqualTo(4);
    assertThat(hits.get(0).getRerankScore()).isEqualTo(0.98);
    assertThat(hits.get(3).getRerankScore()).isEqualTo(0.20);
  }

  @Test
  void rerankFailureDegradesToRrfOrder() {
    stubNonEmptyBase();
    when(rerankResolver.resolveOrNull())
        .thenReturn(new RerankEndpoint(1L, "https://api.example.com", "sk-x", "bge-m3", 30));
    when(chunkMapper.hybridSearch(anyString(), anyString(), Mockito.anyInt()))
        .thenReturn(candidates(16));
    when(rerankClient.rerankQuiet(any(), anyString(), any(), Mockito.anyInt()))
        .thenReturn(null);

    List<KbSearchHit> hits = service.search("问题", 4);

    // 精排上游失败：降级为 RRF 融合顺序 Top-K，rerankScore 保持 null
    assertThat(hits).hasSize(4);
    assertThat(hits.get(0).getSeq()).isEqualTo(0);
    assertThat(hits.get(0).getRerankScore()).isNull();
  }
}
