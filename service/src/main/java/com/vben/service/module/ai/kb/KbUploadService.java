package com.vben.service.module.ai.kb;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vben.service.common.BizException;
import com.vben.service.common.storage.StoredFile;
import com.vben.service.common.storage.StorageService;
import com.vben.service.module.ai.kb.entity.KbDoc;
import com.vben.service.module.ai.kb.entity.KbFile;
import com.vben.service.module.ai.kb.entity.KbUploadSession;
import com.vben.service.module.ai.kb.mapper.KbFileMapper;
import com.vben.service.module.ai.kb.mapper.KbUploadSessionMapper;
import com.vben.service.security.LoginUser;
import com.vben.service.security.LoginUserHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 知识库大文件分片上传服务（设计 D3/D4）：
 * init（幂等领取会话 / 秒传）→ chunk（流式落盘 + jsonb 原子追加索引）
 * → complete（顺序合并 + SHA-256 校验 + StorageService 入库 + 建文档入管线）→ abort（清理）。
 *
 * <p>分片暂存本地 {@code files/kb-chunks/{uploadId}/}（minio 部署时也走本地盘，
 * 合并后进对象存储——分片是短暂暂存，不值得对象化）。合并全程流式不经堆内全量字节。
 */
@Slf4j
@Service
public class KbUploadService {

  /** 分片路径单文件上限 200MB */
  public static final long MAX_FILE_SIZE = 200L * 1024 * 1024;
  /** 单片大小约束 1-10MB */
  public static final long MIN_CHUNK_SIZE = 1024L * 1024;
  public static final long MAX_CHUNK_SIZE = 10L * 1024 * 1024;
  /** 分片数量上限（200MB / 5MB = 40） */
  public static final int MAX_TOTAL_CHUNKS = 40;
  /** 单用户进行中会话上限 */
  public static final int MAX_ACTIVE_SESSIONS = 5;
  /** 会话过期时间（小时） */
  public static final int SESSION_TTL_HOURS = 24;

  private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");
  private static final int STATUS_UPLOADING = 0;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final KbUploadSessionMapper sessionMapper;
  private final KbFileMapper fileMapper;
  private final KbDocService docService;
  private final StorageService storageService;
  /** 分片临时根目录（files/ 整体已 gitignore） */
  private final Path chunksRoot;

  /** 生产构造器（双构造器需显式标注主构造器；5 参变体仅供测试重定向分片目录） */
  @Autowired
  public KbUploadService(KbUploadSessionMapper sessionMapper, KbFileMapper fileMapper,
                         KbDocService docService, StorageService storageService) {
    this(sessionMapper, fileMapper, docService, storageService, Path.of("files", "kb-chunks"));
  }

  /** 测试用：分片临时根目录可重定向到 @TempDir */
  KbUploadService(KbUploadSessionMapper sessionMapper, KbFileMapper fileMapper,
                  KbDocService docService, StorageService storageService, Path chunksRoot) {
    this.sessionMapper = sessionMapper;
    this.fileMapper = fileMapper;
    this.docService = docService;
    this.storageService = storageService;
    this.chunksRoot = chunksRoot;
  }

  /** init 请求体 */
  public record InitReq(String fileName, Long fileSize, String sha256, Long chunkSize) {
  }

  /**
   * 领取上传会话：幂等（同用户同哈希同大小的进行中会话直接复用）；
   * SHA-256 命中已入库文件 → 秒传建文档，不产生会话。
   */
  public Map<String, Object> init(InitReq req) {
    String fileName = req.fileName() == null ? "" : req.fileName().strip();
    if (fileName.isEmpty()) {
      throw BizException.badRequest("error.kb.upload.empty");
    }
    if (fileName.length() > 255) {
      fileName = fileName.substring(0, 255);
    }
    String ext = KbDocService.extension(fileName);
    if (!KbDocService.ALLOWED_EXTENSIONS.contains(ext)) {
      throw BizException.badRequest("error.kb.upload.badType", fileName);
    }
    long fileSize = req.fileSize() == null ? 0 : req.fileSize();
    if (fileSize <= 0) {
      throw BizException.badRequest("error.kb.upload.empty");
    }
    if (fileSize > MAX_FILE_SIZE) {
      throw BizException.badRequest("error.kb.upload.fileTooLarge");
    }
    String sha256 = req.sha256() == null ? "" : req.sha256().strip();
    if (!SHA256_PATTERN.matcher(sha256).matches()) {
      throw BizException.badRequest("error.kb.upload.badHash");
    }
    long chunkSize = req.chunkSize() == null ? 0 : req.chunkSize();
    if (chunkSize < MIN_CHUNK_SIZE || chunkSize > MAX_CHUNK_SIZE) {
      throw BizException.badRequest("error.kb.upload.chunkSize");
    }
    int totalChunks = (int) ((fileSize + chunkSize - 1) / chunkSize);
    if (totalChunks > MAX_TOTAL_CHUNKS) {
      throw BizException.badRequest("error.kb.upload.tooManyChunks");
    }

    // 秒传：整文件哈希命中且有有效引用 → 直接建文档零上传
    KbFile existingFile = fileMapper.selectBySha256(sha256);
    if (existingFile != null && existingFile.getRefCount() != null && existingFile.getRefCount() > 0) {
      fileMapper.incrementRefCount(existingFile.getId());
      try {
        Long docId = docService.createDocAndEnqueue(fileName, fileSize, existingFile.getId(), null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instant", true);
        result.put("docId", docId);
        return result;
      } catch (TaskRejectedException e) {
        fileMapper.decrementRefCount(existingFile.getId());
        throw BizException.badRequest("error.kb.busy");
      }
    }

    LoginUser user = LoginUserHolder.require();
    // 断点续传：同用户同哈希同大小的进行中会话复用
    KbUploadSession existing = sessionMapper.selectOne(new QueryWrapper<KbUploadSession>()
        .eq("user_id", user.getUserId())
        .eq("sha256", sha256)
        .eq("file_size", fileSize)
        .eq("status", STATUS_UPLOADING)
        .last("LIMIT 1"));
    if (existing != null) {
      return sessionResult(existing, false);
    }

    Long count = sessionMapper.selectCount(new QueryWrapper<KbUploadSession>()
        .eq("user_id", user.getUserId())
        .eq("status", STATUS_UPLOADING));
    if (count != null && count >= MAX_ACTIVE_SESSIONS) {
      throw BizException.badRequest("error.kb.upload.sessionLimit");
    }

    KbUploadSession session = new KbUploadSession();
    session.setUploadId(UUID.randomUUID().toString());
    session.setSha256(sha256.toLowerCase(java.util.Locale.ROOT));
    session.setFileName(fileName);
    session.setFileSize(fileSize);
    session.setChunkSize(chunkSize);
    session.setTotalChunks(totalChunks);
    session.setReceivedChunks("[]");
    session.setStatus(STATUS_UPLOADING);
    session.setUserId(user.getUserId());
    session.setExpireAt(LocalDateTime.now().plusHours(SESSION_TTL_HOURS));
    sessionMapper.insertSession(session);
    return sessionResult(session, false);
  }

  /** 上传单片：会话归属/过期校验 → 流式落盘 → jsonb 原子追加索引 */
  public void chunk(String uploadId, int index, MultipartFile part) {
    KbUploadSession session = requireActiveSession(uploadId);
    if (index < 0 || index >= session.getTotalChunks()) {
      throw BizException.badRequest("error.kb.upload.badIndex");
    }
    // 末片允许小于声明单片；其余必须与声明一致（保证合并大小与哈希可对账）
    long expected = index == session.getTotalChunks() - 1
        ? session.getFileSize() - session.getChunkSize() * (session.getTotalChunks() - 1)
        : session.getChunkSize();
    if (part == null || part.getSize() != expected) {
      throw BizException.badRequest("error.kb.upload.chunkSize");
    }
    Path dir = chunksRoot.resolve(session.getUploadId());
    try {
      Files.createDirectories(dir);
      try (InputStream in = part.getInputStream()) {
        Files.copy(in, dir.resolve("chunk-" + index), StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("分片写入磁盘失败", e);
    }
    if (sessionMapper.appendReceivedChunk(uploadId, index) == 0) {
      // 追加零行 = 会话已被并发 abort/清理
      throw BizException.badRequest("error.kb.upload.sessionExpired");
    }
  }

  /**
   * 完成上传：顺序合并分片（流式）→ 校验整文件 SHA-256 → StorageService 入库
   * → 建 kb_file/文档并投递管线 → 清理会话与临时分片。任何失败清理现场。
   */
  public Long complete(String uploadId) {
    KbUploadSession session = requireActiveSession(uploadId);
    List<Integer> received = parseReceived(session);
    if (received.size() != session.getTotalChunks()) {
      throw BizException.badRequest("error.kb.upload.incomplete");
    }

    Path dir = chunksRoot.resolve(session.getUploadId());
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JVM 缺少 SHA-256 实现", e);
    }
    Path merged = dir.resolve("merged.bin");
    try {
      try (OutputStream out = new DigestOutputStream(Files.newOutputStream(merged), digest)) {
        for (int i = 0; i < session.getTotalChunks(); i++) {
          try (InputStream in = Files.newInputStream(dir.resolve("chunk-" + i))) {
            in.transferTo(out);
          }
        }
      }
      String actual = HexFormat.of().formatHex(digest.digest());
      if (!actual.equalsIgnoreCase(session.getSha256())) {
        throw BizException.badRequest("error.kb.upload.hashMismatch");
      }
      Long fileId = storeMerged(session, merged);
      try {
        Long docId = docService.createDocAndEnqueue(session.getFileName(), session.getFileSize(), fileId, null);
        cleanupSession(session, dir);
        return docId;
      } catch (TaskRejectedException e) {
        // 受理失败：回滚本次文件引用（管线未建立，文档未产生）
        KbFile file = fileMapper.selectById(fileId);
        if (file != null) {
          fileMapper.decrementRefCount(fileId);
          if (file.getRefCount() == null || file.getRefCount() <= 1) {
            fileMapper.deleteById(fileId);
            storageService.delete(file.getStorageKey());
          }
        }
        throw BizException.badRequest("error.kb.busy");
      }
    } catch (BizException e) {
      // 业务失败（哈希不匹配/队列打满）：清理会话与临时分片
      cleanupSession(session, dir);
      throw e;
    } catch (IOException e) {
      deleteDirQuietly(dir);
      throw new UncheckedIOException("分片合并失败", e);
    }
  }

  /** 合并产物入库：SHA-256 已存在则复用（并发秒传竞态），否则登记 kb_file */
  private Long storeMerged(KbUploadSession session, Path merged) throws IOException {
    KbFile existing = fileMapper.selectBySha256(session.getSha256());
    if (existing != null && existing.getRefCount() != null && existing.getRefCount() > 0) {
      fileMapper.incrementRefCount(existing.getId());
      return existing.getId();
    }
    StoredFile stored;
    try (InputStream in = Files.newInputStream(merged)) {
      stored = storageService.store(in, session.getFileName(), session.getFileSize());
    }
    KbFile entity = new KbFile();
    entity.setSha256(session.getSha256());
    entity.setSize(session.getFileSize());
    entity.setStorageKey(stored.key());
    entity.setRefCount(1);
    try {
      fileMapper.insert(entity);
      return entity.getId();
    } catch (DuplicateKeyException e) {
      KbFile winner = fileMapper.selectBySha256(session.getSha256());
      storageService.delete(stored.key());
      if (winner == null) {
        throw e;
      }
      fileMapper.incrementRefCount(winner.getId());
      return winner.getId();
    }
  }

  /** 放弃上传：清理会话与临时分片（幂等，不存在视为已清理） */
  public void abort(String uploadId) {
    KbUploadSession session = sessionMapper.selectOne(
        new QueryWrapper<KbUploadSession>().eq("upload_id", uploadId));
    if (session == null) {
      return;
    }
    requireOwnership(session);
    sessionMapper.deleteById(session.getId());
    deleteDirQuietly(chunksRoot.resolve(session.getUploadId()));
  }

  /** 过期会话清理（每小时）：删临时目录 + 删记录，防残留泄漏磁盘 */
  @Scheduled(fixedDelay = 3_600_000, initialDelay = 600_000)
  public void cleanupExpiredSessions() {
    List<KbUploadSession> expired = sessionMapper.selectList(
        new QueryWrapper<KbUploadSession>().lt("expire_at", LocalDateTime.now()));
    for (KbUploadSession session : expired) {
      log.info("清理过期分片上传会话 {}（{}）", session.getUploadId(), session.getFileName());
      sessionMapper.deleteById(session.getId());
      deleteDirQuietly(chunksRoot.resolve(session.getUploadId()));
    }
  }

  // ------------------------------------------------------------------
  // 内部工具
  // ------------------------------------------------------------------

  private KbUploadSession requireActiveSession(String uploadId) {
    KbUploadSession session = sessionMapper.selectOne(
        new QueryWrapper<KbUploadSession>().eq("upload_id", uploadId));
    if (session == null || session.getStatus() == null || session.getStatus() != STATUS_UPLOADING) {
      throw BizException.badRequest("error.kb.upload.sessionExpired");
    }
    requireOwnership(session);
    if (session.getExpireAt() != null && session.getExpireAt().isBefore(LocalDateTime.now())) {
      throw BizException.badRequest("error.kb.upload.sessionExpired");
    }
    return session;
  }

  private void requireOwnership(KbUploadSession session) {
    LoginUser user = LoginUserHolder.require();
    if (!user.getUserId().equals(session.getUserId())) {
      // 会话归属他人：按不存在处理，不泄露会话存在性
      throw BizException.badRequest("error.kb.upload.sessionExpired");
    }
  }

  private Map<String, Object> sessionResult(KbUploadSession session, boolean instant) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("instant", instant);
    result.put("uploadId", session.getUploadId());
    result.put("receivedChunks", parseReceived(session));
    result.put("chunkSize", session.getChunkSize());
    result.put("totalChunks", session.getTotalChunks());
    return result;
  }

  private static List<Integer> parseReceived(KbUploadSession session) {
    try {
      List<Integer> list = MAPPER.readValue(session.getReceivedChunks() == null
          ? "[]" : session.getReceivedChunks(),
          MAPPER.getTypeFactory().constructCollectionType(List.class, Integer.class));
      return list.stream().distinct().sorted().toList();
    } catch (IOException e) {
      throw new IllegalStateException("分片会话索引解析失败", e);
    }
  }

  /** 会话终态清理：删记录 + 删临时目录（目录含合并产物） */
  private void cleanupSession(KbUploadSession session, Path dir) {
    sessionMapper.deleteById(session.getId());
    deleteDirQuietly(dir);
  }

  private void deleteDirQuietly(Path dir) {
    if (!Files.exists(dir)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(dir)) {
      walk.sorted(Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.deleteIfExists(path);
        } catch (IOException e) {
          log.warn("临时分片删除失败：{}", path, e);
        }
      });
    } catch (IOException e) {
      log.warn("临时分片目录遍历失败：{}", dir, e);
    }
  }
}
