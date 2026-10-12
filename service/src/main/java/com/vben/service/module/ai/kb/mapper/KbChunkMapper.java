package com.vben.service.module.ai.kb.mapper;

import com.vben.service.module.ai.kb.KbSearchHit;
import com.vben.service.module.ai.kb.entity.KbChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 知识库向量块 Mapper。
 *
 * <p>embedding 列为 pgvector 的 vector 类型，MyBatis-Plus 无法直接映射，
 * 写入/检索使用显式 SQL：向量以文本字面量（"[0.1,0.2,...]"）传参后
 * {@code CAST(#{v} AS vector)}（预编译参数 text → vector 转换已在 pgvector 验证）。
 * MVP 规模（数千块）暴力扫描即可，不建向量索引。
 */
@Mapper
public interface KbChunkMapper {

  /**
   * 插入一个向量块（切块阶段：正文 + 全文分词，embedding 置空由向量化阶段回填）。
   * tokens 为 {@link com.vben.service.module.ai.kb.KbTokenizer} 产物，空串/null 时 content_tsv 为 NULL。
   */
  @Insert("INSERT INTO kb_chunk (doc_id, seq, content, content_tsv) "
      + "VALUES (#{docId}, #{seq}, #{content}, to_tsvector('simple', #{tokens}))")
  int insertChunkText(@Param("docId") Long docId, @Param("seq") int seq,
      @Param("content") String content, @Param("tokens") String tokens);

  /** 回填一个块的向量（embedding 阶段逐块更新，embedding IS NULL 即待处理断点） */
  @Update("UPDATE kb_chunk SET embedding = CAST(#{embedding} AS vector) "
      + "WHERE doc_id = #{docId} AND seq = #{seq}")
  int updateEmbedding(@Param("docId") Long docId, @Param("seq") int seq, @Param("embedding") String embedding);

  /** 取一批待向量化块（embedding 为空即断点之后未处理的部分，按 seq 有序） */
  @Select("SELECT id, doc_id AS docId, seq, content FROM kb_chunk "
      + "WHERE doc_id = #{docId} AND embedding IS NULL ORDER BY seq LIMIT #{limit}")
  List<KbChunk> selectPendingChunks(@Param("docId") Long docId, @Param("limit") int limit);

  /** 按文档级联删除全部块 */
  @Delete("DELETE FROM kb_chunk WHERE doc_id = #{docId}")
  int deleteByDocId(@Param("docId") Long docId);

  /** 纯向量检索退化路径（查询无有效分词 token 时使用）；仅命中完成状态文档 */
  @Select("SELECT c.id AS id, c.doc_id AS docId, c.seq AS seq, c.content AS content, "
      + "d.file_name AS fileName, 1 - (c.embedding <=> CAST(#{queryVector} AS vector)) AS vecScore, "
      + "'vector' AS matchType, 1.0 / (60 + ROW_NUMBER() OVER (ORDER BY c.embedding <=> CAST(#{queryVector} AS vector))) AS score "
      + "FROM kb_chunk c JOIN kb_doc d ON d.id = c.doc_id "
      + "WHERE d.status = 1 AND c.embedding IS NOT NULL "
      + "ORDER BY c.embedding <=> CAST(#{queryVector} AS vector) LIMIT #{topK}")
  List<KbSearchHit> vectorSearch(@Param("queryVector") String queryVector, @Param("topK") int topK);

  /**
   * 混合检索：向量（余弦 Top-50）与全文（ts_rank Top-50）两路候选 RRF 融合
   * （score = 1/(60+rank_v) + 1/(60+rank_f)，缺通道按 9999 兜底仅计另一路），
   * 按融合分降序取 Top-K；两路均仅命中完成状态文档，失败/处理中的块不产生检索污染。
   */
  @Select("WITH v AS (SELECT c.id, ROW_NUMBER() OVER (ORDER BY c.embedding <=> CAST(#{queryVector} AS vector)) AS r "
      + "FROM kb_chunk c JOIN kb_doc d ON d.id = c.doc_id "
      + "WHERE d.status = 1 AND c.embedding IS NOT NULL "
      + "ORDER BY c.embedding <=> CAST(#{queryVector} AS vector) LIMIT 50), "
      + "f AS (SELECT c.id, ROW_NUMBER() OVER (ORDER BY ts_rank(c.content_tsv, plainto_tsquery('simple', #{queryTokens})) DESC) AS r "
      + "FROM kb_chunk c JOIN kb_doc d ON d.id = c.doc_id "
      + "WHERE d.status = 1 AND c.content_tsv @@ plainto_tsquery('simple', #{queryTokens}) "
      + "LIMIT 50) "
      + "SELECT c.id AS id, c.doc_id AS docId, c.seq AS seq, c.content AS content, "
      + "d.file_name AS fileName, 1 - (c.embedding <=> CAST(#{queryVector} AS vector)) AS vecScore, "
      + "CASE WHEN v.id IS NOT NULL AND f.id IS NOT NULL THEN 'both' "
      + "WHEN v.id IS NOT NULL THEN 'vector' ELSE 'fts' END AS matchType, "
      + "1.0 / (60 + COALESCE(v.r, 9999)) + 1.0 / (60 + COALESCE(f.r, 9999)) AS score "
      + "FROM v FULL OUTER JOIN f ON v.id = f.id "
      + "JOIN kb_chunk c ON c.id = COALESCE(v.id, f.id) "
      + "JOIN kb_doc d ON d.id = c.doc_id "
      + "ORDER BY score DESC LIMIT #{topK}")
  List<KbSearchHit> hybridSearch(@Param("queryVector") String queryVector,
      @Param("queryTokens") String queryTokens, @Param("topK") int topK);

  /** 缺失全文索引的块数（启动回填判定） */
  @Select("SELECT COUNT(*) FROM kb_chunk WHERE content_tsv IS NULL")
  long countMissingTsv();

  /** 取一批缺失全文索引的块（按 id 有序分批回填） */
  @Select("SELECT id, content FROM kb_chunk WHERE content_tsv IS NULL ORDER BY id LIMIT #{limit}")
  List<KbChunk> selectMissingTsvBatch(@Param("limit") int limit);

  /** 回填一个块的全文索引（tokens 空时写 NULL，与写入路径同语义） */
  @Update("UPDATE kb_chunk SET content_tsv = to_tsvector('simple', #{tokens}) WHERE id = #{id}")
  int updateTsv(@Param("id") Long id, @Param("tokens") String tokens);

  /** 库中现存向量维度集合（空表返回空列表；用于维度一致性校验） */
  @Select("SELECT DISTINCT vector_dims(embedding) FROM kb_chunk")
  List<Integer> distinctDimensions();

  /** 库中向量块总数 */
  @Select("SELECT COUNT(*) FROM kb_chunk")
  long countAll();
}
