package com.vben.service.module.ai.kb;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.vben.service.config.AsyncConfig;
import com.vben.service.module.ai.emb.ActiveEmbeddingResolver;
import com.vben.service.module.ai.emb.EmbEndpoint;
import com.vben.service.module.ai.emb.EmbeddingClient;
import com.vben.service.module.ai.kb.entity.KbChunk;
import com.vben.service.module.ai.kb.entity.KbDoc;
import com.vben.service.module.ai.kb.mapper.KbChunkMapper;
import com.vben.service.module.ai.kb.mapper.KbDocMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库处理管线执行器（设计 D6）：解析 → 切块 → 批量向量化，阶段/进度持久化、断点续跑。
 *
 * <p>运行在单 worker 线程池（{@link AsyncConfig#KB_INGEST_EXECUTOR}，手动投递以便
 * 维护执行中文档集合），串行消费避免上游限流叠加。断点机制：切块阶段先落纯文本块
 * 行（embedding 为空），向量化按批回填并推进 processed_chunks——任一时刻失败/重启，
 * 重试都只处理 embedding 为空的块，已完成块不重复调用上游。
 * 阶段进度：PARSING 0-20%，CHUNKING 20-40%，EMBEDDING 40-100%（按已处理块数线性）。
 * 失败保留已切块行（断点），检索 SQL 仅命中完成状态文档，不产生检索污染。
 */
@Slf4j
@Service
public class KbPipelineExecutor {

  /** 处理阶段常量（与 kb_doc.stage 列一致） */
  public static final String STAGE_PARSING = "PARSING";
  public static final String STAGE_CHUNKING = "CHUNKING";
  public static final String STAGE_EMBEDDING = "EMBEDDING";

  /** 单文档切块总量配额（超过即失败提示拆分，保护上游与库容量） */
  public static final int CHUNK_QUOTA = 20000;

  /** 向量化批次大小（每批调用一次上游并落一次断点） */
  static final int EMBED_BATCH = 64;

  /** 重启恢复扫描宽限期（秒）：刚投递的文档 updateTime 未刷新前不被误判 */
  static final int RECOVERY_GRACE_SECONDS = 60;

  private static final int STATUS_PROCESSING = 0;
  private static final int STATUS_DONE = 1;
  private static final int STATUS_FAILED = 2;

  private final KbDocMapper docMapper;
  private final KbChunkMapper chunkMapper;
  private final ActiveEmbeddingResolver embeddingResolver;
  private final EmbeddingClient embeddingClient;
  private final KbExtractorRouter extractorRouter;
  private final com.vben.service.common.storage.StorageService storageService;
  private final com.vben.service.module.ai.kb.mapper.KbFileMapper fileMapper;
  private final org.springframework.core.task.TaskExecutor worker;
  /** 执行中文档 id 集合：重启恢复扫描据此跳过在跑文档 */
  private final Set<Long> executing = ConcurrentHashMap.newKeySet();

  public KbPipelineExecutor(KbDocMapper docMapper, KbChunkMapper chunkMapper,
                            ActiveEmbeddingResolver embeddingResolver, EmbeddingClient embeddingClient,
                            KbExtractorRouter extractorRouter,
                            com.vben.service.common.storage.StorageService storageService,
                            com.vben.service.module.ai.kb.mapper.KbFileMapper fileMapper,
                            @Qualifier(AsyncConfig.KB_INGEST_EXECUTOR) org.springframework.core.task.TaskExecutor worker) {
    this.docMapper = docMapper;
    this.chunkMapper = chunkMapper;
    this.embeddingResolver = embeddingResolver;
    this.embeddingClient = embeddingClient;
    this.extractorRouter = extractorRouter;
    this.storageService = storageService;
    this.fileMapper = fileMapper;
    this.worker = worker;
  }

  /**
   * 受理后投递管线（队列打满抛 TaskRejectedException，调用方负责补偿回滚文档）。
   *
   * @param docId   文档 id
   * @param content 旧路径正文（≤10MB md/txt 直接在内存传入）；存储路径传 null（按 fileId 读存储）
   */
  public void submit(Long docId, String content) {
    executing.add(docId);
    try {
      worker.execute(() -> {
        try {
          process(docId, content);
        } finally {
          executing.remove(docId);
        }
      });
    } catch (TaskRejectedException e) {
      executing.remove(docId);
      throw e;
    }
  }

  /** 管线主体：幂等（重复投递/非处理中状态直接返回） */
  void process(Long docId, String content) {
    KbDoc doc = docMapper.selectById(docId);
    if (doc == null || doc.getStatus() == null || doc.getStatus() != STATUS_PROCESSING) {
      return;
    }
    try {
      EmbEndpoint endpoint = embeddingResolver.requireActive();
      boolean chunked = doc.getTotalChunks() != null && doc.getTotalChunks() > 0;
      int total;
      int processed;
      if (chunked) {
        // 断点续跑：切块产物已在库，直接进入向量化阶段
        total = doc.getTotalChunks();
        processed = doc.getProcessedChunks() == null ? 0 : doc.getProcessedChunks();
        updateStage(docId, STAGE_EMBEDDING, embedProgress(processed, total));
      } else {
        total = parseAndChunk(doc, content);
        processed = 0;
      }
      embedAll(docId, endpoint, total, processed);
      done(docId, total);
      log.info("知识库文档 {}（{}）处理完成：{} 块", docId, doc.getFileName(), total);
    } catch (Exception e) {
      String reason = e.getMessage() == null || e.getMessage().isBlank()
          ? e.getClass().getSimpleName() : e.getMessage();
      fail(docId, reason);
    }
  }

  /** 解析 + 切块：返回 [总块数]；产物纯文本块行落库（embedding 空）并持久化 total_chunks 断点 */
  private int parseAndChunk(KbDoc doc, String content) throws Exception {
    Long docId = doc.getId();
    updateStage(docId, STAGE_PARSING, 5);

    List<String> chunks = new ArrayList<>();
    boolean hasText;
    if (content != null) {
      // 旧路径：正文已在内存，保持原切块语义（md 按标题 / txt 按段落）
      chunks.addAll(KbTextChunker.chunk(content, isMarkdown(doc.getFileName())));
      hasText = !content.isBlank();
    } else {
      // 存储路径：提取器流式产段，逐段切块，内存常数
      hasText = false;
      try (InputStream in = storageService.open(requireStorageKey(doc));
           DocumentTextExtractor.TextSegments segments = extractorRouter.route(extension(doc.getFileName())).segments(in)) {
        chunkMapper.deleteByDocId(docId); // 幂等：重切块前清残留（中断遗留的部分块行）
        while (segments.hasNext()) {
          String segment = segments.next();
          if (segment != null && !segment.isBlank()) {
            hasText = true;
            chunks.addAll(KbTextChunker.chunkSegment(segment));
          }
        }
      }
      if (!hasText) {
        throw new IllegalArgumentException("未识别到文本内容（可能是扫描件），请更换文字版文档");
      }
    }
    if (chunks.isEmpty()) {
      throw new IllegalArgumentException("文档内容为空或无法切块");
    }
    if (chunks.size() > CHUNK_QUOTA) {
      throw new IllegalArgumentException(
          "文档切块数 " + chunks.size() + " 超过配额 " + CHUNK_QUOTA + "，请拆分文档后重新上传");
    }

    updateStage(docId, STAGE_CHUNKING, 20);
    if (content != null) {
      chunkMapper.deleteByDocId(docId);
    }
    for (int i = 0; i < chunks.size(); i++) {
      chunkMapper.insertChunkText(docId, i, chunks.get(i), KbTokenizer.tokenize(chunks.get(i)));
    }
    // 断点：total_chunks 持久化后，重启/重试都只走向量化阶段
    docMapper.update(null, new UpdateWrapper<KbDoc>()
        .eq("id", docId)
        .set("total_chunks", chunks.size())
        .set("stage", STAGE_EMBEDDING)
        .set("progress", 40));
    return chunks.size();
  }

  /** 批量向量化：每批回填向量并推进 processed_chunks/progress 断点 */
  private void embedAll(Long docId, EmbEndpoint endpoint, int total, int processed) {
    while (true) {
      List<KbChunk> pending = chunkMapper.selectPendingChunks(docId, EMBED_BATCH);
      if (pending.isEmpty()) {
        return;
      }
      List<String> texts = pending.stream().map(KbChunk::getContent).toList();
      EmbeddingClient.EmbedResult result = embeddingClient.embed(endpoint, texts);
      for (int i = 0; i < pending.size(); i++) {
        chunkMapper.updateEmbedding(docId, pending.get(i).getSeq(),
            KbSearchService.toVectorString(result.vectors().get(i)));
      }
      processed += pending.size();
      docMapper.update(null, new UpdateWrapper<KbDoc>()
          .eq("id", docId)
          .set("processed_chunks", processed)
          .set("progress", embedProgress(processed, total)));
      if (pending.size() < EMBED_BATCH) {
        return;
      }
    }
  }

  private void done(Long docId, int total) {
    docMapper.update(null, new UpdateWrapper<KbDoc>()
        .eq("id", docId)
        .set("status", STATUS_DONE)
        .set("chunk_count", total)
        .set("stage", null)
        .set("progress", 100)
        .set("error_msg", null));
  }

  /** 置失败（可读原因截断 500），保留块行作断点；stage 清空，检索仅命中完成态文档 */
  private void fail(Long docId, String reason) {
    log.warn("知识库文档 {} 处理失败：{}", docId, reason);
    docMapper.update(null, new UpdateWrapper<KbDoc>()
        .eq("id", docId)
        .set("status", STATUS_FAILED)
        .set("stage", null)
        .set("error_msg", reason.substring(0, Math.min(reason.length(), 500))));
  }

  private void updateStage(Long docId, String stage, int progress) {
    docMapper.update(null, new UpdateWrapper<KbDoc>()
        .eq("id", docId)
        .set("stage", stage)
        .set("progress", progress));
  }

  private static int embedProgress(int processed, int total) {
    if (total <= 0) {
      return 40;
    }
    return Math.min(99, 40 + (int) Math.round(60.0 * processed / total));
  }

  /** 重启恢复（设计 D6）：每 30s 扫「处理中」且不在执行集合、超过宽限期的文档置失败，重试从断点续跑 */
  @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
  public void recoverInterrupted() {
    List<KbDoc> stuck = docMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<KbDoc>()
        .eq("status", STATUS_PROCESSING)
        .lt("update_time", LocalDateTime.now().minusSeconds(RECOVERY_GRACE_SECONDS)));
    for (KbDoc doc : stuck) {
      if (executing.contains(doc.getId())) {
        continue;
      }
      log.warn("知识库文档 {} 检测为服务重启遗留的处理中状态，置失败待重试", doc.getId());
      fail(doc.getId(), "服务重启导致处理中断，可重试从断点续传");
    }
  }

  /** 由 doc.fileId 解析存储键；无 fileId（旧路径文档）且切块产物已失 → 可读错误引导重传 */
  private String requireStorageKey(KbDoc doc) {
    if (doc.getFileId() == null) {
      throw new IllegalStateException("源内容不存在（旧路径文档不落存储且切块产物丢失），请删除后重新上传");
    }
    var file = fileMapper.selectById(doc.getFileId());
    if (file == null || file.getStorageKey() == null) {
      throw new IllegalStateException("文档源文件记录缺失，请删除后重新上传");
    }
    return file.getStorageKey();
  }

  static boolean isMarkdown(String fileName) {
    String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
    return lower.endsWith(".md") || lower.endsWith(".markdown");
  }

  static String extension(String fileName) {
    String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
    int dot = lower.lastIndexOf('.');
    return dot < 0 ? "" : lower.substring(dot + 1);
  }
}
