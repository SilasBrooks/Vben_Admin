package com.vben.service.module.ai.kb;

import com.vben.service.common.BizException;
import com.vben.service.module.ai.emb.ActiveEmbeddingResolver;
import com.vben.service.module.ai.emb.EmbeddingClient;
import com.vben.service.module.ai.kb.mapper.KbChunkMapper;
import com.vben.service.module.ai.rerank.ActiveRerankResolver;
import com.vben.service.module.ai.rerank.RerankClient;
import com.vben.service.module.ai.rerank.RerankEndpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库混合检索：查询文本 → 激活 Embedding 向量化 + 应用侧分词 →
 * pgvector 余弦与 tsvector 全文两路召回 RRF 融合 → 可插拔 Rerank 精排。
 *
 * <p>供管理页「检索测试」与 AI 工具 search_knowledge 复用；
 * 查询前校验查询向量维度与库中现存维度一致，避免混选型期间的脏结果；
 * 查询无有效分词 token（纯符号等）时退化为纯向量检索。
 *
 * <p>精排层可插拔：存在激活 Rerank 配置时召回放宽后精排取 Top-K（rerankScore 有值）；
 * 未配置/未激活时直接返回 RRF 融合 Top-K；精排上游失败时降级 RRF 顺序
 * （rerankScore 全空），检索与 AI 问答永不因精排故障中断。
 */
@Service
@RequiredArgsConstructor
public class KbSearchService {

  /** 默认召回条数 */
  public static final int DEFAULT_TOP_K = 4;

  /** 精排召回候选数上限（Rerank 单次请求文档数上限的保守值） */
  private static final int RECALL_LIMIT = 30;

  private final KbChunkMapper chunkMapper;
  private final ActiveEmbeddingResolver embeddingResolver;
  private final EmbeddingClient embeddingClient;
  private final ActiveRerankResolver rerankResolver;
  private final RerankClient rerankClient;

  /**
   * 混合 Top-K 检索（有激活精排配置时经 Rerank 重排）。
   *
   * @param query 查询文本
   * @param topK  召回条数（1-10）
   * @return 按最终得分降序的命中列表；知识库为空返回空列表
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

    // 精排层：有激活配置时召回放宽（候选为最终 Top-K 的 4 倍以上），无配置时保持 Top-K
    RerankEndpoint rerank = rerankResolver.resolveOrNull();
    int recall = rerank == null ? k : Math.min(Math.max(k * 4, 16), RECALL_LIMIT);

    String vector = toVectorString(queryVector);
    String tokens = KbTokenizer.tokenize(query);
    List<KbSearchHit> hits;
    if (tokens.isEmpty()) {
      // 纯符号等无 token 查询：空 tsquery 无匹配意义，退化纯向量
      hits = chunkMapper.vectorSearch(vector, recall);
    } else {
      hits = chunkMapper.hybridSearch(vector, tokens, recall);
    }
    if (hits.size() <= k) {
      return hits;
    }
    return rerank == null ? hits.subList(0, k) : rerank(hits, query, rerank, k);
  }

  /**
   * Rerank 精排：对放宽召回的融合候选逐对打分，按相关性降序取 Top-K。
   * 精排失败（{@code rerankQuiet} 返回 null）降级为 RRF 融合顺序 Top-K，
   * rerankScore 保持 null，由前端按「融合分」展示。
   */
  private List<KbSearchHit> rerank(List<KbSearchHit> hits, String query,
                                   RerankEndpoint endpoint, int k) {
    List<String> documents = hits.stream().map(KbSearchHit::getContent).toList();
    List<RerankClient.RerankHit> ranked = rerankClient.rerankQuiet(
        endpoint, query, documents, hits.size());
    if (ranked == null) {
      return hits.subList(0, k);
    }
    List<KbSearchHit> result = new ArrayList<>(Math.min(k, ranked.size()));
    for (RerankClient.RerankHit hit : ranked) {
      if (result.size() == k) {
        break;
      }
      if (hit.index() < 0 || hit.index() >= hits.size()) {
        continue;
      }
      KbSearchHit source = hits.get(hit.index());
      source.setRerankScore(Math.round(hit.relevanceScore() * 10000) / 10000.0);
      result.add(source);
    }
    return result;
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
