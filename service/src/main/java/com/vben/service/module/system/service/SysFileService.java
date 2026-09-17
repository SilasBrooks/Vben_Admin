package com.vben.service.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vben.service.common.BizException;
import com.vben.service.common.storage.StorageService;
import com.vben.service.common.storage.StoredFile;
import com.vben.service.module.system.entity.SysFile;
import com.vben.service.module.system.entity.SysUser;
import com.vben.service.module.system.mapper.SysFileMapper;
import com.vben.service.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * 文件服务：上传校验（扩展名白名单/大小）→ 存储 → 落库；删除同步移除物理文件。
 */
@Service
@RequiredArgsConstructor
public class SysFileService {

  /** 通用上传白名单：图片 / 文档 / 压缩包 */
  private static final Set<String> GENERAL_WHITELIST = Set.of(
      "jpg", "jpeg", "png", "gif", "webp",
      "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt",
      "zip");

  /** 头像白名单：仅图片 */
  private static final Set<String> IMAGE_WHITELIST = Set.of("jpg", "jpeg", "png", "gif", "webp");

  /** 通用上传大小上限（与 spring.servlet.multipart.max-file-size 一致） */
  public static final long MAX_SIZE = 10 * 1024 * 1024L;

  /** 头像大小上限 */
  public static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024L;

  private final SysFileMapper fileMapper;
  private final SysUserMapper userMapper;
  private final StorageService storageService;

  /**
   * 通用上传：白名单 + 10MB → 存储 → 落库（biz_type=general）
   */
  public SysFile upload(InputStream in, String originalName, long size, Long uploaderId) {
    return doUpload(in, originalName, size, uploaderId, "general", GENERAL_WHITELIST, MAX_SIZE);
  }

  /**
   * 头像上传：仅图片白名单 + 5MB → 存储 → 落库（biz_type=avatar）
   */
  public SysFile uploadAvatar(InputStream in, String originalName, long size, Long uploaderId) {
    return doUpload(in, originalName, size, uploaderId, "avatar", IMAGE_WHITELIST, MAX_AVATAR_SIZE);
  }

  private SysFile doUpload(InputStream in, String originalName, long size, Long uploaderId,
      String bizType, Set<String> whitelist, long maxSize) {
    if (!StringUtils.hasText(originalName)) {
      throw BizException.badRequest("文件名不能为空");
    }
    String ext = StringUtils.getFilenameExtension(originalName);
    if (ext == null || !whitelist.contains(ext.toLowerCase())) {
      throw BizException.badRequest("不支持的文件类型: " + (ext == null ? "(无扩展名)" : "." + ext));
    }
    if (size > maxSize) {
      throw BizException.badRequest("文件大小超过限制（最大 " + (maxSize / 1024 / 1024) + "MB）");
    }
    // 存储名随机 UUID，防路径穿越与文件名猜测；content_type 由原始名推导，不取客户端字段
    StoredFile stored = storageService.store(in, originalName);
    SysFile entity = new SysFile();
    entity.setOriginalName(truncate(originalName, 255));
    entity.setStorageKey(stored.key());
    entity.setSize(stored.size());
    entity.setContentType(resolveContentType(originalName));
    entity.setBizType(bizType);
    entity.setUploaderId(uploaderId);
    fileMapper.insert(entity);
    return entity;
  }

  /** 分页：原始名模糊过滤，按上传时间倒序；批量回填上传人用户名 */
  public IPage<SysFile> page(long pageNo, long pageSize, String originalName) {
    QueryWrapper<SysFile> q = new QueryWrapper<>();
    if (originalName != null && !originalName.isBlank()) {
      q.like("original_name", originalName);
    }
    q.orderByDesc("create_time");
    IPage<SysFile> page = fileMapper.selectPage(new Page<>(pageNo, pageSize), q);
    List<Long> uploaderIds = page.getRecords().stream()
        .map(SysFile::getUploaderId).filter(id -> id != null).distinct().toList();
    if (!uploaderIds.isEmpty()) {
      java.util.Map<Long, String> nameById = new java.util.HashMap<>();
      for (SysUser u : userMapper.selectBatchIds(uploaderIds)) {
        nameById.put(u.getId(), u.getNickname() != null && !u.getNickname().isBlank()
            ? u.getNickname() : u.getUsername());
      }
      page.getRecords().forEach(f -> f.setUploaderName(nameById.get(f.getUploaderId())));
    }
    return page;
  }

  public SysFile getById(Long id) {
    return fileMapper.selectById(id);
  }

  /** 删除：记录与物理文件同步移除；物理文件不存在时容错（幂等） */
  public void delete(Long id) {
    SysFile file = fileMapper.selectById(id);
    if (file == null) {
      throw BizException.badRequest("文件不存在或已删除");
    }
    fileMapper.deleteById(id);
    try {
      storageService.delete(file.getStorageKey());
    } catch (Exception ignored) {
      // 物理删除失败不阻断记录删除：孤儿文件无引用不暴露
    }
  }

  /** 按原始名推导 Content-Type（Spring 内置 MediaTypeFactory，推导不出时用流类型兜底） */
  public static String resolveContentType(String originalName) {
    return MediaTypeFactory.getMediaType(originalName)
        .map(org.springframework.http.MediaType::toString)
        .orElse("application/octet-stream");
  }

  private static String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max);
  }

  /** 前端展示项：id + 原始名 + 访问路径 */
  public record FileItem(Long id, String originalName, String url, long size, String contentType) {
    public static FileItem from(SysFile f) {
      return new FileItem(f.getId(), f.getOriginalName(), "/api/file/" + f.getId() + "/content",
          f.getSize(), f.getContentType());
    }
  }
}
