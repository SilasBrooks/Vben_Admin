package com.vben.service.module.ai.kb;

import com.vben.service.module.ai.kb.entity.KbChunk;
import com.vben.service.module.ai.kb.mapper.KbChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 存量块全文索引回填：升级后启动时把 content_tsv 为 NULL 的历史块
 * 按同一分词器补齐（与升级 SQL 分离——SQL 初始化器不支持 dollar-quoted，
 * 分词逻辑也无法在纯 SQL 中表达，与「不用数据库触发器」约束同思路）。
 *
 * <p>幂等：仅处理缺失行，可重复启动；失败仅记日志不阻塞服务启动，
 * 下次启动继续补齐。回填期间新写入路径自带分词，互不干扰。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KbTsvBackfill implements ApplicationRunner {

  /** 每批回填行数 */
  private static final int BATCH = 500;

  private final KbChunkMapper chunkMapper;

  @Override
  public void run(ApplicationArguments args) {
    try {
      long missing = chunkMapper.countMissingTsv();
      if (missing == 0) {
        return;
      }
      log.info("知识库全文索引回填开始：待补齐 {} 块", missing);
      long done = 0;
      while (done < missing) {
        List<KbChunk> batch = chunkMapper.selectMissingTsvBatch(BATCH);
        if (batch.isEmpty()) {
          break;
        }
        for (KbChunk chunk : batch) {
          chunkMapper.updateTsv(chunk.getId(), KbTokenizer.tokenize(chunk.getContent()));
          done++;
        }
      }
      log.info("知识库全文索引回填完成：共 {} 块", done);
    } catch (Exception e) {
      log.warn("知识库全文索引回填失败（不影响启动，下次启动继续）: {}", e.getMessage());
    }
  }
}
