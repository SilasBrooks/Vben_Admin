package com.vben.service.module.ai.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vben.service.module.ai.kb.entity.KbUploadSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 知识库分片上传会话 Mapper（必须置于 mapper 子包，见 @MapperScan 扫描规则）。
 *
 * <p>received_chunks 为 jsonb：插入用显式 CAST，追加用「jsonb || + 去重排序」
 * 单条 SQL 原子完成——分片并发到达时不经「读-合并-写」应用层竞争（设计 D3/Risks）。
 */
@Mapper
public interface KbUploadSessionMapper extends BaseMapper<KbUploadSession> {

  /** 插入会话（received_chunks 以 JSON 文本参数显式 CAST 为 jsonb） */
  @Insert("INSERT INTO kb_upload_session (upload_id, sha256, file_name, file_size, chunk_size, "
      + "total_chunks, received_chunks, status, user_id, expire_at) "
      + "VALUES (#{uploadId}, #{sha256}, #{fileName}, #{fileSize}, #{chunkSize}, #{totalChunks}, "
      + "CAST(#{receivedChunks} AS jsonb), #{status}, #{userId}, #{expireAt})")
  int insertSession(KbUploadSession session);

  /**
   * 原子追加分片索引：jsonb 数组连接 + DISTINCT 去重 + 按数值排序，
   * 单 SQL 完成，并发重复上传同一片也不会产生重复索引。
   */
  @Update("UPDATE kb_upload_session SET received_chunks = ("
      + "SELECT COALESCE(jsonb_agg(v ORDER BY (v #>> '{}')::int), '[]'::jsonb) "
      + "FROM jsonb_array_elements(received_chunks || to_jsonb(CAST(#{index} AS int))) AS t(v)), "
      + "update_time = CURRENT_TIMESTAMP "
      + "WHERE upload_id = #{uploadId} AND status = 0")
  int appendReceivedChunk(@Param("uploadId") String uploadId, @Param("index") int index);
}
