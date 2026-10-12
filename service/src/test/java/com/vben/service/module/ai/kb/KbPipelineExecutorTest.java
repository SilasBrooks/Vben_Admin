package com.vben.service.module.ai.kb;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.vben.service.common.storage.StorageService;
import com.vben.service.module.ai.emb.ActiveEmbeddingResolver;
import com.vben.service.module.ai.emb.EmbEndpoint;
import com.vben.service.module.ai.emb.EmbeddingClient;
import com.vben.service.module.ai.kb.entity.KbChunk;
import com.vben.service.module.ai.kb.entity.KbDoc;
import com.vben.service.module.ai.kb.entity.KbFile;
import com.vben.service.module.ai.kb.mapper.KbChunkMapper;
import com.vben.service.module.ai.kb.mapper.KbDocMapper;
import com.vben.service.module.ai.kb.mapper.KbFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库处理管线单测（同步执行器内联运行）：
 * 阶段/进度流转、断点续跑（processed_chunks 之后的块才向量化）、
 * 块数配额超限失败、空文本（扫描件）失败、重启遗留恢复。
 */
class KbPipelineExecutorTest {

  private static final long DOC_ID = 9L;

  private final KbDocMapper docMapper = mock(KbDocMapper.class);
  private final KbChunkMapper chunkMapper = mock(KbChunkMapper.class);
  private final ActiveEmbeddingResolver resolver = mock(ActiveEmbeddingResolver.class);
  private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
  private final KbFileMapper fileMapper = mock(KbFileMapper.class);
  private final StorageService storageService = mock(StorageService.class);
  private final KbPipelineExecutor executor = new KbPipelineExecutor(
      docMapper, chunkMapper, resolver, embeddingClient,
      new KbExtractorRouter(List.of(new PlainTextExtractor(), new PdfTextExtractor(), new DocxTextExtractor())),
      storageService, fileMapper, Runnable::run);

  @BeforeEach
  void setUp() {
    when(resolver.requireActive())
        .thenReturn(new EmbEndpoint("https://api.example.com", "sk-x", "bge-m3", null, 30));
  }

  private KbDoc doc(String fileName, Long fileId, Integer totalChunks, Integer processed) {
    KbDoc doc = new KbDoc();
    doc.setId(DOC_ID);
    doc.setFileName(fileName);
    doc.setStatus(0);
    doc.setFileId(fileId);
    doc.setTotalChunks(totalChunks);
    doc.setProcessedChunks(processed);
    return doc;
  }

  private void stubEmbed() {
    // 返回向量数与请求文本数一致（真实客户端语义），避免多块批次下索引越界
    when(embeddingClient.embed(any(), anyList())).thenAnswer(inv -> {
      List<String> texts = inv.getArgument(1);
      List<float[]> vectors = new java.util.ArrayList<>();
      for (int i = 0; i < texts.size(); i++) {
        vectors.add(new float[] {0.1f, 0.2f});
      }
      return new EmbeddingClient.EmbedResult(vectors, 2);
    });
  }

  @Test
  void oldPathMarkdownFlowCompletesWithChunksAndProgress() {
    stubEmbed();
    when(docMapper.selectById(DOC_ID)).thenReturn(doc("手册.md", null, 0, 0));
    // 切块落库后，待向量化查询返回两块再返回空
    KbChunk c0 = new KbChunk();
    c0.setDocId(DOC_ID);
    c0.setSeq(0);
    c0.setContent("# 标题一\n内容甲。");
    KbChunk c1 = new KbChunk();
    c1.setDocId(DOC_ID);
    c1.setSeq(1);
    c1.setContent("# 标题二\n内容乙。");
    when(chunkMapper.selectPendingChunks(DOC_ID, KbPipelineExecutor.EMBED_BATCH))
        .thenReturn(List.of(c0, c1), List.of());

    executor.submit(DOC_ID, "# 标题一\n内容甲。\n\n# 标题二\n内容乙。");

    // Markdown 标题切块：2 块；切块前清残留，再落文本块行，最后逐块回填向量
    verify(chunkMapper).deleteByDocId(DOC_ID);
    verify(chunkMapper, times(2)).insertChunkText(eq(DOC_ID), anyInt(), anyString(), anyString());
    verify(chunkMapper, times(2)).updateEmbedding(eq(DOC_ID), anyInt(), eq("[0.1,0.2]"));
    verify(docMapper, Mockito.atLeastOnce()).update(Mockito.isNull(), any(UpdateWrapper.class));
  }

  @Test
  void storedPathExtractsTextAndFailsOnEmptyExtraction() {
    KbDoc doc = doc("扫描件.pdf", 5L, 0, 0);
    when(docMapper.selectById(DOC_ID)).thenReturn(doc);
    KbFile file = new com.vben.service.module.ai.kb.entity.KbFile();
    file.setId(5L);
    file.setStorageKey("k.pdf");
    when(fileMapper.selectById(5L)).thenReturn(file);
    when(storageService.open("k.pdf"))
        .thenReturn(new ByteArrayInputStream(new byte[0]));

    executor.submit(DOC_ID, null);

    verify(embeddingClient, never()).embed(any(), anyList());
    verify(chunkMapper, never()).insertChunkText(any(), anyInt(), any(), any());
    verify(docMapper, Mockito.atLeastOnce()).update(Mockito.isNull(), any(UpdateWrapper.class));
  }

  @Test
  void storedPathTxtCompletes() {
    stubEmbed();
    KbDoc doc = doc("笔记.txt", 5L, 0, 0);
    when(docMapper.selectById(DOC_ID)).thenReturn(doc);
    KbFile file = new KbFile();
    file.setId(5L);
    file.setStorageKey("k.txt");
    when(fileMapper.selectById(5L)).thenReturn(file);
    when(storageService.open("k.txt")).thenReturn(new ByteArrayInputStream(
        "存储路径正文段落。".getBytes(StandardCharsets.UTF_8)));
    KbChunk pending = new KbChunk();
    pending.setDocId(DOC_ID);
    pending.setSeq(0);
    pending.setContent("存储路径正文段落。");
    when(chunkMapper.selectPendingChunks(DOC_ID, KbPipelineExecutor.EMBED_BATCH))
        .thenReturn(List.of(pending), List.of());

    executor.submit(DOC_ID, null);

    verify(chunkMapper, times(1)).insertChunkText(eq(DOC_ID), eq(0), anyString(), anyString());
    verify(chunkMapper).updateEmbedding(DOC_ID, 0, "[0.1,0.2]");
  }

  @Test
  void resumeSkipsChunkingAndEmbedsOnlyPendingChunks() {
    stubEmbed();
    when(docMapper.selectById(DOC_ID)).thenReturn(doc("手册.md", null, 4, 3));
    KbChunk pending = new KbChunk();
    pending.setDocId(DOC_ID);
    pending.setSeq(3);
    pending.setContent("剩余块");
    when(chunkMapper.selectPendingChunks(DOC_ID, KbPipelineExecutor.EMBED_BATCH))
        .thenReturn(List.of(pending), List.of());

    executor.submit(DOC_ID, null);

    // 断点续跑：不再解析/切块（旧路径无正文也不报错），只向量化剩余块
    verify(chunkMapper, never()).insertChunkText(any(), anyInt(), any(), any());
    verify(chunkMapper).updateEmbedding(DOC_ID, 3, "[0.1,0.2]");
  }

  @Test
  void oldPathResumeWithoutChunksFailsWithReadableReason() {
    when(docMapper.selectById(DOC_ID)).thenReturn(doc("手册.md", null, 0, 0));
    when(chunkMapper.selectPendingChunks(DOC_ID, KbPipelineExecutor.EMBED_BATCH))
        .thenReturn(List.of());

    executor.submit(DOC_ID, null);

    verify(embeddingClient, never()).embed(any(), anyList());
    verify(docMapper, Mockito.atLeastOnce()).update(Mockito.isNull(), any(UpdateWrapper.class));
  }

  @Test
  void quotaExceededFailsWithSplitHint() {
    when(docMapper.selectById(DOC_ID)).thenReturn(doc("大.txt", null, 0, 0));
    // 20001 个 700 字符段落（每段独占一块）→ 超过配额 20000
    String content = ("段".repeat(700) + "\n\n").repeat(20001);

    executor.submit(DOC_ID, content);

    verify(chunkMapper, never()).insertChunkText(any(), anyInt(), any(), any());
    verify(embeddingClient, never()).embed(any(), anyList());
  }

  @Test
  void recoverInterruptedMarksStuckDocsFailed() {
    KbDoc stuck = doc("遗留.md", null, 0, 0);
    when(docMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(stuck));

    executor.recoverInterrupted();

    verify(docMapper).update(Mockito.isNull(), any(UpdateWrapper.class));
    verify(chunkMapper, never()).deleteByDocId(anyLong());
  }

  @Test
  void recoverSkipsNonProcessingDocs() {
    when(docMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

    executor.recoverInterrupted();

    verify(docMapper, never()).update(Mockito.isNull(), any(UpdateWrapper.class));
  }
}
