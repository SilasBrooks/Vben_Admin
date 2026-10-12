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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 知识库混合检索服务单测：向量字面量格式、Top-K 收敛（1-10）、空库短路与维度一致性校验、
 * 纯符号查询退化纯向量路径。
 */
class KbSearchServiceTest {

  private final KbChunkMapper chunkMapper = Mockito.mock(KbChunkMapper.class);
  private final ActiveEmbeddingResolver resolver = Mockito.mock(ActiveEmbeddingResolver.class);
  private final EmbeddingClient embeddingClient = Mockito.mock(EmbeddingClient.class);
  private final KbSearchService service =
      new KbSearchService(chunkMapper, resolver, embeddingClient);

  private void stubNonEmptyBase() {
    when(chunkMapper.countAll()).thenReturn(5L);
    when(resolver.requireActive())
        .thenReturn(new EmbEndpoint("https://api.example.com", "sk-x", "bge-m3", null, 30));
    when(embeddingClient.embedOne(any(), anyString())).thenReturn(new float[] {0.1f});
    when(chunkMapper.distinctDimensions()).thenReturn(List.of(1));
    when(chunkMapper.hybridSearch(anyString(), anyString(), Mockito.anyInt()))
        .thenReturn(List.of());
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
}
