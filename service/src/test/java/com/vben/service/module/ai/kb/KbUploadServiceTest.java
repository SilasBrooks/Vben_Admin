package com.vben.service.module.ai.kb;

import com.vben.service.common.BizException;
import com.vben.service.common.storage.StoredFile;
import com.vben.service.common.storage.StorageService;
import com.vben.service.module.ai.kb.entity.KbFile;
import com.vben.service.module.ai.kb.entity.KbUploadSession;
import com.vben.service.module.ai.kb.mapper.KbFileMapper;
import com.vben.service.module.ai.kb.mapper.KbUploadSessionMapper;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分片上传服务单测（mock mapper/StorageService，分片目录重定向 @TempDir）：
 * init 幂等复用、秒传命中、chunk 落盘与非法索引拒绝、complete 哈希不匹配清理、
 * 哈希匹配入库建文档、abort 清理、会话数上限。
 */
class KbUploadServiceTest {

  private static final long USER_ID = 1L;

  @TempDir
  Path tempDir;

  private final KbUploadSessionMapper sessionMapper = mock(KbUploadSessionMapper.class);
  private final KbFileMapper fileMapper = mock(KbFileMapper.class);
  private final KbDocService docService = mock(KbDocService.class);
  private final StorageService storageService = mock(StorageService.class);
  private KbUploadService service;

  @BeforeEach
  void setUp() {
    service = new KbUploadService(sessionMapper, fileMapper, docService, storageService, tempDir);
    LoginUserHolder.set(new LoginUser(USER_ID, "vben", List.of("super"), null, 0));
  }

  @AfterEach
  void tearDown() {
    LoginUserHolder.clear();
  }

  private static final String SHA_A = "a".repeat(64);
  /** init 校验单片下限 1MB，测试数据按合法值构造（2.5MB / 1MB = 3 片） */
  private static final long MB = 1024L * 1024;
  private static final long FILE_SIZE = 2_500_000L;
  private static final int TOTAL_CHUNKS = 3;

  private KbUploadSession session(int totalChunks, long chunkSize, long fileSize, String sha) {
    KbUploadSession s = new KbUploadSession();
    s.setId(10L);
    s.setUploadId("u-1");
    s.setSha256(sha);
    s.setFileName("大文件.pdf");
    s.setFileSize(fileSize);
    s.setChunkSize(chunkSize);
    s.setTotalChunks(totalChunks);
    s.setReceivedChunks("[]");
    s.setStatus(0);
    s.setUserId(USER_ID);
    s.setExpireAt(LocalDateTime.now().plusHours(24));
    return s;
  }

  private static String sha256(byte[] data) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
  }

  // ------------------------------------------------------------------
  // init
  // ------------------------------------------------------------------

  @Test
  void initReusesActiveSessionForResume() {
    KbUploadSession existing = session(TOTAL_CHUNKS, MB, FILE_SIZE, SHA_A);
    existing.setReceivedChunks("[0,1]");
    when(sessionMapper.selectOne(any())).thenReturn(existing);

    var result = service.init(new KbUploadService.InitReq("大文件.pdf", FILE_SIZE, SHA_A, MB));

    assertEquals("u-1", result.get("uploadId"));
    assertEquals(List.of(0, 1), result.get("receivedChunks"));
    verify(sessionMapper, never()).insertSession(any());
  }

  @Test
  void initInstantHitCreatesDocWithoutSession() {
    KbFile file = new KbFile();
    file.setId(66L);
    file.setSha256(SHA_A);
    file.setRefCount(2);
    when(fileMapper.selectBySha256(SHA_A)).thenReturn(file);
    when(docService.createDocAndEnqueue("大文件.pdf", FILE_SIZE, 66L, null)).thenReturn(9L);

    var result = service.init(new KbUploadService.InitReq("大文件.pdf", FILE_SIZE, SHA_A, MB));

    assertEquals(true, result.get("instant"));
    assertEquals(9L, result.get("docId"));
    verify(fileMapper).incrementRefCount(66L);
    verify(sessionMapper, never()).insertSession(any());
  }

  @Test
  void initRejectsBadExtensionAndChunkSizeAndTooManyChunks() {
    assertThrows(BizException.class, () ->
        service.init(new KbUploadService.InitReq("病毒.exe", 12L, SHA_A, 5L)));
    assertThrows(BizException.class, () ->
        service.init(new KbUploadService.InitReq("a.pdf", 12L, "bad", 5L)));
    assertThrows(BizException.class, () ->
        service.init(new KbUploadService.InitReq("a.pdf", 12L, SHA_A, 500L)));
    // 100MB / 1MB = 100 片 > 40
    assertThrows(BizException.class, () ->
        service.init(new KbUploadService.InitReq("a.pdf", 100L * 1024 * 1024, SHA_A, 1024L * 1024)));
  }

  @Test
  void initRejectsWhenActiveSessionLimitReached() {
    when(sessionMapper.selectOne(any())).thenReturn(null);
    when(sessionMapper.selectCount(any())).thenReturn(5L);

    assertThrows(BizException.class, () ->
        service.init(new KbUploadService.InitReq("a.pdf", FILE_SIZE, SHA_A, MB)));
  }

  // ------------------------------------------------------------------
  // chunk
  // ------------------------------------------------------------------

  @Test
  void chunkWritesFileToTempDirAndAppendsIndex() throws Exception {
    KbUploadSession s = session(3, 5, 12, SHA_A);
    when(sessionMapper.selectOne(any())).thenReturn(s);
    when(sessionMapper.appendReceivedChunk("u-1", 0)).thenReturn(1);
    byte[] data = "01234".getBytes();
    var part = new MockMultipartFile("chunk", "blob", "application/octet-stream", data);

    service.chunk("u-1", 0, part);

    assertTrue(Files.exists(tempDir.resolve("u-1").resolve("chunk-0")));
    verify(sessionMapper).appendReceivedChunk("u-1", 0);
  }

  @Test
  void chunkRejectsInvalidIndexAndSizeMismatch() {
    KbUploadSession s = session(3, 5, 12, SHA_A);
    when(sessionMapper.selectOne(any())).thenReturn(s);

    assertThrows(BizException.class, () ->
        service.chunk("u-1", 3, new MockMultipartFile("chunk", new byte[] {1})));
    assertThrows(BizException.class, () ->
        service.chunk("u-1", 0, new MockMultipartFile("chunk", new byte[] {1, 2})));
  }

  // ------------------------------------------------------------------
  // complete
  // ------------------------------------------------------------------

  @Test
  void completeRejectsIncompleteChunks() {
    KbUploadSession s = session(3, 5, 12, SHA_A);
    when(sessionMapper.selectOne(any())).thenReturn(s);

    assertThrows(BizException.class, () -> service.complete("u-1"));
  }

  @Test
  void completeHashMismatchCleansSessionAndTempDir() throws Exception {
    KbUploadSession s = session(2, 3, 6, SHA_A);
    s.setReceivedChunks("[0,1]");
    when(sessionMapper.selectOne(any())).thenReturn(s);
    writeChunks("u-1", 2, "wrong-content".getBytes());

    assertThrows(BizException.class, () -> service.complete("u-1"));

    verify(sessionMapper).deleteById(10L);
    assertFalse(Files.exists(tempDir.resolve("u-1")));
    verify(docService, never()).createDocAndEnqueue(any(), anyLong(), any(), any());
  }

  @Test
  void completeHashMatchStoresAndCreatesDoc() throws Exception {
    byte[] content = "0123456789".getBytes();
    String sha = sha256(content);
    KbUploadSession s = session(2, 8, 10, sha);
    s.setReceivedChunks("[0,1]");
    when(sessionMapper.selectOne(any())).thenReturn(s);
    writeSlices("u-1", content, 8);
    when(fileMapper.selectBySha256(sha)).thenReturn(null);
    when(storageService.store(any(), eq("大文件.pdf"), eq(10L)))
        .thenReturn(new StoredFile("2026/10/10/x.pdf", 10L));
    when(docService.createDocAndEnqueue("大文件.pdf", 10L, null, null)).thenReturn(9L);

    Long docId = service.complete("u-1");

    assertEquals(9L, docId);
    verify(fileMapper).insert(any(KbFile.class));
    verify(sessionMapper).deleteById(10L);
    assertFalse(Files.exists(tempDir.resolve("u-1")));
  }

  @Test
  void completeReusesExistingFileOnShaHit() throws Exception {
    byte[] content = "0123456789".getBytes();
    String sha = sha256(content);
    KbUploadSession s = session(2, 8, 10, sha);
    s.setReceivedChunks("[0,1]");
    when(sessionMapper.selectOne(any())).thenReturn(s);
    writeSlices("u-1", content, 8);
    KbFile existing = new KbFile();
    existing.setId(66L);
    existing.setSha256(sha);
    existing.setRefCount(1);
    existing.setStorageKey("old-key");
    when(fileMapper.selectBySha256(sha)).thenReturn(existing);
    when(docService.createDocAndEnqueue("大文件.pdf", 10L, 66L, null)).thenReturn(9L);

    service.complete("u-1");

    verify(fileMapper).incrementRefCount(66L);
    verify(fileMapper, never()).insert(any(KbFile.class));
    verify(storageService, never()).store(any(), anyString(), anyLong());
  }

  // ------------------------------------------------------------------
  // abort / cleanup
  // ------------------------------------------------------------------

  @Test
  void abortDeletesSessionAndTempDir() throws Exception {
    KbUploadSession s = session(3, 5, 12, SHA_A);
    when(sessionMapper.selectOne(any())).thenReturn(s);
    writeChunks("u-1", 1, "abc".getBytes());

    service.abort("u-1");

    verify(sessionMapper).deleteById(10L);
    assertFalse(Files.exists(tempDir.resolve("u-1")));
  }

  @Test
  void abortUnknownSessionIsSilent() {
    when(sessionMapper.selectOne(any())).thenReturn(null);
    service.abort("nope");
    verify(sessionMapper, never()).deleteById(anyLong());
  }

  @Test
  void cleanupExpiredRemovesRecordAndDir() throws Exception {
    KbUploadSession s = session(3, 5, 12, SHA_A);
    s.setExpireAt(LocalDateTime.now().minusHours(1));
    when(sessionMapper.selectList(any())).thenReturn(List.of(s));
    writeChunks("u-1", 1, "abc".getBytes());

    service.cleanupExpiredSessions();

    verify(sessionMapper).deleteById(10L);
    assertFalse(Files.exists(tempDir.resolve("u-1")));
  }

  @Test
  void chunkForForeignSessionIsRejected() {
    KbUploadSession s = session(3, 5, 12, SHA_A);
    s.setUserId(999L);
    when(sessionMapper.selectOne(any())).thenReturn(s);

    assertThrows(BizException.class, () ->
        service.chunk("u-1", 0, new MockMultipartFile("chunk", new byte[] {1, 2, 3, 4, 5})));
  }

  // ------------------------------------------------------------------
  // 工具
  // ------------------------------------------------------------------

  private void writeChunks(String uploadId, int count, byte[] content) throws Exception {
    Path dir = tempDir.resolve(uploadId);
    Files.createDirectories(dir);
    for (int i = 0; i < count; i++) {
      Files.write(dir.resolve("chunk-" + i), content);
    }
  }

  /** 按 slice 大小切分 content 写入分片文件（末片为余量），合并即可还原原内容 */
  private void writeSlices(String uploadId, byte[] content, int slice) throws Exception {
    Path dir = tempDir.resolve(uploadId);
    Files.createDirectories(dir);
    int total = (content.length + slice - 1) / slice;
    for (int i = 0; i < total; i++) {
      int from = i * slice;
      int to = Math.min(from + slice, content.length);
      Files.write(dir.resolve("chunk-" + i), java.util.Arrays.copyOfRange(content, from, to));
    }
  }
}
