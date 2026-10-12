package com.vben.service.module.ai.kb;

import com.vben.service.common.BizException;
import com.vben.service.common.storage.StoredFile;
import com.vben.service.common.storage.StorageService;
import com.vben.service.module.ai.kb.entity.KbDoc;
import com.vben.service.module.ai.kb.entity.KbFile;
import com.vben.service.module.ai.kb.mapper.KbChunkMapper;
import com.vben.service.module.ai.kb.mapper.KbDocMapper;
import com.vben.service.module.ai.kb.mapper.KbFileMapper;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档服务单测：md/txt 旧路径与 pdf/docx 转存路径受理、
 * 删除的引用计数语义（递减/归零清理存储）、重试状态机、队列打满补偿。
 */
class KbDocServiceTest {

  private final KbDocMapper docMapper = mock(KbDocMapper.class);
  private final KbChunkMapper chunkMapper = mock(KbChunkMapper.class);
  private final KbFileMapper fileMapper = mock(KbFileMapper.class);
  private final StorageService storageService = mock(StorageService.class);
  private final KbPipelineExecutor pipelineExecutor = mock(KbPipelineExecutor.class);
  private final KbDocService service = new KbDocService(
      docMapper, chunkMapper, fileMapper, storageService, pipelineExecutor);

  @BeforeEach
  void setUp() {
    LoginUserHolder.set(new LoginUser(1L, "vben", List.of("super"), null, 0));
    Mockito.doAnswer(inv -> {
      KbDoc doc = inv.getArgument(0);
      doc.setId(88L);
      return 1;
    }).when(docMapper).insert(any(KbDoc.class));
  }

  @AfterEach
  void tearDown() {
    LoginUserHolder.clear();
  }

  // ------------------------------------------------------------------
  // 上传受理
  // ------------------------------------------------------------------

  @Test
  void mdUploadKeepsLegacyInMemoryContentPath() {
    var file = new MockMultipartFile("file", "指南.md", "text/plain",
        "# 标题\n正文".getBytes(StandardCharsets.UTF_8));

    Long id = service.upload(file);

    assertEquals(88L, id);
    ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
    verify(pipelineExecutor).submit(eq(88L), content.capture());
    assertTrue(content.getValue().contains("正文"));
    verify(fileMapper, never()).insert(any(KbFile.class));
  }

  @Test
  void pdfUploadStoresToStorageAndRegistersFile() throws Exception {
    var file = new MockMultipartFile("file", "手册.pdf", "application/pdf",
            "%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8));
    when(storageService.store(any(), eq("手册.pdf"), eq(file.getSize())))
        .thenReturn(new StoredFile("2026/10/10/a.pdf", file.getSize()));
    when(fileMapper.selectBySha256(any())).thenReturn(null);

    service.upload(file);

    ArgumentCaptor<KbFile> captor = ArgumentCaptor.forClass(KbFile.class);
    verify(fileMapper).insert(captor.capture());
    assertEquals(64, captor.getValue().getSha256().length(), "应登记 64 位十六进制 SHA-256");
    assertEquals(1, captor.getValue().getRefCount());
    verify(pipelineExecutor).submit(eq(88L), isNull());
  }

  @Test
  void pdfUploadDeduplicatesSameSha() throws Exception {
    var file = new MockMultipartFile("file", "手册.pdf", "application/pdf",
            "%PDF-1.4 same".getBytes(StandardCharsets.UTF_8));
    when(storageService.store(any(), eq("手册.pdf"), eq(file.getSize())))
        .thenReturn(new StoredFile("dup.pdf", file.getSize()));
    KbFile existing = new KbFile();
    existing.setId(66L);
    existing.setStorageKey("old-key");
    existing.setRefCount(1);
    when(fileMapper.selectBySha256(any())).thenReturn(existing);

    service.upload(file);

    verify(fileMapper).incrementRefCount(66L);
    verify(fileMapper, never()).insert(any(KbFile.class));
    verify(storageService).delete("dup.pdf");
    verify(storageService, never()).delete("old-key");
  }

  @Test
  void uploadRejectsBadExtensionAndOversize() {
    var exe = new MockMultipartFile("file", "木马.exe", "application/octet-stream", new byte[] {1});
    assertThrows(BizException.class, () -> service.upload(exe));
    var big = new MockMultipartFile("file", "大.md", "text/plain",
        new byte[(int) (KbDocService.MAX_SIZE_BYTES + 1)]);
    assertThrows(BizException.class, () -> service.upload(big));
  }

  @Test
  void queueFullCompensatesDocAndFileRef() throws Exception {
    var file = new MockMultipartFile("file", "手册.pdf", "application/pdf",
            "%PDF-1.4 x".getBytes(StandardCharsets.UTF_8));
    when(storageService.store(any(), eq("手册.pdf"), eq(file.getSize())))
        .thenReturn(new StoredFile("k.pdf", file.getSize()));
    when(fileMapper.selectBySha256(any())).thenReturn(null);
    Mockito.doThrow(new TaskRejectedException("queue full"))
        .when(pipelineExecutor).submit(any(), any());
    KbFile inserted = new KbFile();
    inserted.setId(66L);
    inserted.setStorageKey("k.pdf");
    inserted.setRefCount(1);
    when(fileMapper.selectById(66L)).thenReturn(inserted);
    Mockito.doAnswer(inv -> {
      KbFile f = inv.getArgument(0);
      f.setId(66L);
      return 1;
    }).when(fileMapper).insert(any(KbFile.class));

    assertThrows(BizException.class, () -> service.upload(file));

    verify(docMapper).deleteById(88L);
    verify(fileMapper).decrementRefCount(66L);
    verify(fileMapper).deleteById(66L);
    verify(storageService).delete("k.pdf");
  }

  // ------------------------------------------------------------------
  // 删除：引用计数语义
  // ------------------------------------------------------------------

  @Test
  void deleteDecrementsRefCountAndCleansStorageWhenZero() {
    KbDoc doc = new KbDoc();
    doc.setId(7L);
    doc.setStatus(1);
    doc.setFileId(66L);
    when(docMapper.selectById(7L)).thenReturn(doc);
    KbFile file = new KbFile();
    file.setId(66L);
    file.setStorageKey("k.pdf");
    file.setRefCount(1);
    when(fileMapper.selectById(66L)).thenReturn(file);

    service.delete(7L);

    verify(chunkMapper).deleteByDocId(7L);
    verify(docMapper).deleteById(7L);
    verify(fileMapper).decrementRefCount(66L);
    verify(fileMapper).deleteById(66L);
    verify(storageService).delete("k.pdf");
  }

  @Test
  void deleteKeepsStorageWhenOtherReferencesRemain() {
    KbDoc doc = new KbDoc();
    doc.setId(7L);
    doc.setStatus(1);
    doc.setFileId(66L);
    when(docMapper.selectById(7L)).thenReturn(doc);
    KbFile file = new KbFile();
    file.setId(66L);
    file.setStorageKey("k.pdf");
    file.setRefCount(2);
    when(fileMapper.selectById(66L)).thenReturn(file);

    service.delete(7L);

    verify(fileMapper).decrementRefCount(66L);
    verify(fileMapper, never()).deleteById(66L);
    verify(storageService, never()).delete(any());
  }

  @Test
  void deleteRejectsProcessingDoc() {
    KbDoc doc = new KbDoc();
    doc.setId(7L);
    doc.setStatus(0);
    when(docMapper.selectById(7L)).thenReturn(doc);

    assertThrows(BizException.class, () -> service.delete(7L));
  }

  // ------------------------------------------------------------------
  // 重试
  // ------------------------------------------------------------------

  @Test
  void retryFailedDocReenqueuesFromBreakpoint() {
    KbDoc doc = new KbDoc();
    doc.setId(7L);
    doc.setStatus(2);
    doc.setFileId(66L);
    when(docMapper.selectById(7L)).thenReturn(doc);

    service.retry(7L);

    verify(docMapper).update(Mockito.isNull(), any());
    verify(pipelineExecutor).submit(7L, null);
  }

  @Test
  void retryRejectsNonFailedDoc() {
    KbDoc doc = new KbDoc();
    doc.setId(7L);
    doc.setStatus(0);
    when(docMapper.selectById(7L)).thenReturn(doc);

    assertThrows(BizException.class, () -> service.retry(7L));
    verify(pipelineExecutor, never()).submit(any(), any());
  }
}
