package com.vben.service.module.ai.kb;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 知识库文档服务：上传受理、分页列表、删除、重试。
 *
 * <p>双路径上传（设计 D1）：≤10MB 单请求受理——md/txt 正文直读内存入库（存量语义），
 * pdf/docx 转存对象存储并登记 kb_file；>10MB 走分片协议（{@link KbUploadService}）。
 * 受理后统一交 {@link KbPipelineExecutor} 异步处理，接口即时返回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbDocService {

  /** 允许的扩展名（小写）：单请求与分片路径共用（分片大小上限另校验） */
  public static final Set<String> ALLOWED_EXTENSIONS = Set.of("md", "txt", "markdown", "pdf", "docx");

  /** 单请求路径大小上限（字节）：分片路径放宽至 200MB（KbUploadService） */
  public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

  private static final int STATUS_PROCESSING = 0;
  private static final int STATUS_FAILED = 2;

  private final KbDocMapper docMapper;
  private final KbChunkMapper chunkMapper;
  private final KbFileMapper fileMapper;
  private final StorageService storageService;
  private final KbPipelineExecutor pipelineExecutor;

  /** 分页列表：文件名模糊过滤，时间倒序（stage/progress 随实体返回） */
  public IPage<KbDoc> page(long pageNo, long pageSize, String fileName) {
    QueryWrapper<KbDoc> q = new QueryWrapper<>();
    if (StringUtils.hasText(fileName)) {
      q.like("file_name", fileName);
    }
    q.orderByDesc("create_time");
    return docMapper.selectPage(new Page<>(pageNo, pageSize), q);
  }

  /** 预览块：块序号与正文（kb_chunk 按 seq 升序即文档正文序列） */
  public record PreviewBlock(int seq, String content) {
  }

  /** 文档原文预览数据：文档名 + 按块序号升序的正文块列表 */
  public record PreviewDoc(String fileName, List<PreviewBlock> blocks) {
  }

  /**
   * 文档原文预览：AI 溯源卡片「查看原文」数据源。
   * 仅要求登录态（Controller 层不加权限码，与 search_knowledge「知识库面向全体登录用户」
   * 语义一致——溯源摘要本就随回答面向全体用户展示，预览未扩大内容暴露面）。
   * 数据源为 kb_chunk（正文切块后的存储形态，预览定位「检索块语境」而非原始排版还原）；
   * 文档不存在报可读错误，完成前无块时返回空 blocks 由前端提示。
   */
  public PreviewDoc preview(Long id) {
    KbDoc doc = docMapper.selectById(id);
    if (doc == null) {
      throw BizException.badRequest("error.kb.notFound");
    }
    List<PreviewBlock> blocks = chunkMapper.selectByDocId(id).stream()
        .map(c -> new PreviewBlock(c.getSeq(), c.getContent()))
        .toList();
    return new PreviewDoc(doc.getFileName(), blocks);
  }

  /** 单请求上传受理：校验 → md/txt 正文入库 / pdf/docx 转存 → 投递管线 */
  public Long upload(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw BizException.badRequest("error.kb.upload.empty");
    }
    String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
    String ext = extension(name);
    if (!ALLOWED_EXTENSIONS.contains(ext)) {
      throw BizException.badRequest("error.kb.upload.badType", name);
    }
    if (file.getSize() > MAX_SIZE_BYTES) {
      throw BizException.badRequest("error.kb.upload.tooLarge");
    }

    if ("pdf".equals(ext) || "docx".equals(ext)) {
      // pdf/docx：转存对象存储 + 登记物理文件，正文由管线按需提取
      Long fileId = storeAndRegisterFile(file, name);
      try {
        return createDocAndEnqueue(name, file.getSize(), fileId, null);
      } catch (TaskRejectedException e) {
        compensateFileRef(fileId);
        throw BizException.badRequest("error.kb.busy");
      }
    }

    // md/txt：正文直读（兼容存量语义）
    String content;
    try {
      content = new String(file.getBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw BizException.badRequest("error.kb.upload.empty");
    }
    if (!StringUtils.hasText(content.strip())) {
      throw BizException.badRequest("error.kb.upload.empty");
    }
    return createDocAndEnqueue(name, file.getSize(), null, content);
  }

  /**
   * 创建文档记录并投递管线；队列打满时删除文档记录并抛出（存储引用由调用方补偿）。
   *
   * @param fileId  物理文件 id（存储路径），旧路径传 null
   * @param content 旧路径正文（存储路径传 null）
   */
  public Long createDocAndEnqueue(String fileName, long sizeBytes, Long fileId, String content) {
    LoginUser user = LoginUserHolder.require();
    KbDoc doc = new KbDoc();
    doc.setFileName(fileName);
    doc.setSizeBytes(sizeBytes);
    doc.setChunkCount(0);
    doc.setStatus(STATUS_PROCESSING);
    doc.setFileId(fileId);
    doc.setStage(KbPipelineExecutor.STAGE_PARSING);
    doc.setProgress(fileId == null ? 0 : 5);
    doc.setProcessedChunks(0);
    doc.setTotalChunks(0);
    doc.setUploaderId(user.getUserId());
    doc.setUploaderName(user.getUsername());
    docMapper.insert(doc);

    try {
      pipelineExecutor.submit(doc.getId(), content);
    } catch (TaskRejectedException e) {
      // 队列打满：删除半成品文档记录，存储引用由调用方补偿
      docMapper.deleteById(doc.getId());
      throw e;
    }
    return doc.getId();
  }

  /**
   * 转存文件并登记 kb_file（SHA-256 边写边算）：命中唯一索引说明同内容文件已存，
   * 复用既有记录并递增引用，删除本次重复落盘的副本。
   */
  private Long storeAndRegisterFile(MultipartFile file, String name) {
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JVM 缺少 SHA-256 实现", e);
    }
    StoredFile stored;
    try (InputStream in = file.getInputStream()) {
      stored = storageService.store(new DigestInputStream(in, digest), name, file.getSize());
    } catch (IOException e) {
      throw BizException.badRequest("error.kb.upload.empty");
    }
    String sha256 = HexFormat.of().formatHex(digest.digest());

    KbFile existing = fileMapper.selectBySha256(sha256);
    if (existing != null) {
      fileMapper.incrementRefCount(existing.getId());
      storageService.delete(stored.key());
      return existing.getId();
    }
    KbFile entity = new KbFile();
    entity.setSha256(sha256);
    entity.setSize(file.getSize());
    entity.setStorageKey(stored.key());
    entity.setRefCount(1);
    try {
      fileMapper.insert(entity);
      return entity.getId();
    } catch (DuplicateKeyException e) {
      // 并发同内容上传：回落到复用既有记录
      KbFile winner = fileMapper.selectBySha256(sha256);
      if (winner == null) {
        throw e;
      }
      fileMapper.incrementRefCount(winner.getId());
      storageService.delete(stored.key());
      return winner.getId();
    }
  }

  /** 队列打满等失败场景：回滚一处物理文件引用（递减，归零清理记录与存储，best-effort） */
  private void compensateFileRef(Long fileId) {
    if (fileId == null) {
      return;
    }
    try {
      KbFile file = fileMapper.selectById(fileId);
      if (file == null) {
        return;
      }
      fileMapper.decrementRefCount(fileId);
      if (file.getRefCount() == null || file.getRefCount() <= 1) {
        fileMapper.deleteById(fileId);
        storageService.delete(file.getStorageKey());
      }
    } catch (Exception e) {
      log.warn("知识库文件引用补偿失败 fileId={}", fileId, e);
    }
  }

  /** 重试：失败文档重新入队，从 processed_chunks 断点续跑（处理中/完成拒绝） */
  public void retry(Long id) {
    KbDoc doc = requireExists(id);
    if (doc.getStatus() == null || doc.getStatus() != STATUS_FAILED) {
      throw BizException.badRequest("error.kb.retryNotAllowed");
    }
    docMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<KbDoc>()
        .eq("id", id)
        .set("status", STATUS_PROCESSING)
        .set("error_msg", null)
        .set("stage", doc.getFileId() != null
            ? KbPipelineExecutor.STAGE_PARSING : KbPipelineExecutor.STAGE_EMBEDDING)
        .set("progress", doc.getFileId() != null ? 5 : 40));
    try {
      pipelineExecutor.submit(id, null);
    } catch (TaskRejectedException e) {
      docMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<KbDoc>()
          .eq("id", id)
          .set("status", STATUS_FAILED)
          .set("stage", null)
          .set("error_msg", "当前处理任务较多，请稍后再试"));
      throw BizException.badRequest("error.kb.busy");
    }
  }

  /**
   * 删除：处理中拒删（避免与管线线程竞态），完成/失败级联清理向量块；
   * 关联物理文件时递减引用计数，归零清理 kb_file 记录与存储文件（best-effort）。
   */
  public void delete(Long id) {
    KbDoc doc = requireExists(id);
    if (doc.getStatus() != null && doc.getStatus() == STATUS_PROCESSING) {
      throw BizException.badRequest("error.kb.deleteProcessing");
    }
    chunkMapper.deleteByDocId(id);
    docMapper.deleteById(id);
    if (doc.getFileId() != null) {
      compensateFileRef(doc.getFileId());
    }
  }

  private KbDoc requireExists(Long id) {
    KbDoc doc = docMapper.selectById(id);
    if (doc == null) {
      throw BizException.badRequest("error.kb.notFound");
    }
    return doc;
  }

  static String extension(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
  }
}
