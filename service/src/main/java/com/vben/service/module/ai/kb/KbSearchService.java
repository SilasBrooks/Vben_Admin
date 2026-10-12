package com.vben.service.module.ai.kb;

import com.vben.service.common.BizException;
import com.vben.service.module.ai.emb.ActiveEmbeddingResolver;
import com.vben.service.module.ai.emb.EmbeddingClient;
import com.vben.service.module.ai.kb.mapper.KbChunkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库混合检索：查询文本 → 激活 Embedding 向量化 + 应用侧分词 →
 * pgvector 余弦与 tsvector 全文两路召回 RRF 融合。
 *
 * <p>供管理页「检索测试」与 AI 工具 search_knowledge 复用；
 * 查询前校验查询向量维度与库中现存维度一致，避免混选型期间的脏结果；
 * 查询无有效分词 token（纯符号等）时退化为纯向量检索。
 */
@Service
@RequiredArgsConstructor
public class KbSearchService {

  /** 默认召回条数 */
  public static final int DEFAULT_TOP_K = 4;

  private final KbChunkMapper chunkMapper;
  private final ActiveEmbeddingResolver embeddingResolver;
  private final EmbeddingClient embeddingClient;

  /**
   * 混合 Top-K 检索。
   *
   * @param query 查询文本
   * @param topK  召回条数（1-10）
   * @return 按融合分降序的命中列表；知识库为空返回空列表
   */
  public List<KbSearchHit> search(String query, int topK) {
    int k = Math.min(Math.max(topK, 1), 10);
    if (chunkMapper.countAll() == 0) {
      return List.of();
    }
    var endpoint = embeddingResolver.requireActive();
    float[] queryVector = embeddingClient.embedOne(endpoint, query);

    // 维度一致性校验：混选型期间提示重建，而非返回错配的相似度
    List<Integer> dims = chunkMapper.distinctDimensions();
    if (!dims.isEmpty() && !dims.contains(queryVector.length)) {
      throw BizException.badRequest("error.kb.dimensionMismatch", queryVector.length, dims.get(0));
    }
    String vector = toVectorString(queryVector);
    String tokens = KbTokenizer.tokenize(query);
    if (tokens.isEmpty()) {
      // 纯符号等无 token 查询：空 tsquery 无匹配意义，退化纯向量
      return chunkMapper.vectorSearch(vector, k);
    }
    return chunkMapper.hybridSearch(vector, tokens, k);
  }

  /** float[] → pgvector 文本字面量（"[0.1,0.2,0.3]"） */
  public static String toVectorString(float[] vector) {
    StringBuilder sb = new StringBuilder(vector.length * 8).append('[');
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(vector[i]);
    }
    return sb.append(']').toString();
  }
}
