package com.vben.service.module.ai.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vben.service.module.ai.kb.entity.KbFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 知识库物理文件 Mapper（必须置于 mapper 子包，见 @MapperScan 扫描规则） */
@Mapper
public interface KbFileMapper extends BaseMapper<KbFile> {

  /** 按 SHA-256 查物理文件（秒传判定） */
  @Select("SELECT * FROM kb_file WHERE sha256 = #{sha256}")
  KbFile selectBySha256(@Param("sha256") String sha256);

  /** 引用计数 +1（新文档关联） */
  @Update("UPDATE kb_file SET ref_count = ref_count + 1 WHERE id = #{id}")
  int incrementRefCount(@Param("id") Long id);

  /** 引用计数 -1（删除文档，不使为负） */
  @Update("UPDATE kb_file SET ref_count = ref_count - 1 WHERE id = #{id} AND ref_count > 0")
  int decrementRefCount(@Param("id") Long id);
}
