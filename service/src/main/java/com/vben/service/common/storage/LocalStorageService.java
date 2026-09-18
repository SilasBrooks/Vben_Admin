package com.vben.service.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 本地磁盘存储实现：{base-path}/yyyy/MM/dd/{uuid}.{ext} 日期分桶。
 * key 即相对存储根目录的路径，统一做白名单字符校验防路径穿越。
 */
@Service
@ConditionalOnProperty(name = "vben.file.storage", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

  private static final DateTimeFormatter BUCKET = DateTimeFormatter.ofPattern("yyyy/MM/dd");

  private final Path basePath;

  public LocalStorageService(StorageProperties properties) {
    this.basePath = Path.of(properties.getLocal().getBasePath()).toAbsolutePath().normalize();
  }

  @Override
  public StoredFile store(InputStream in, String originalName, long size) {
    String ext = StringUtils.getFilenameExtension(originalName);
    String key = LocalDate.now().format(BUCKET) + "/" + UUID.randomUUID() + (ext != null ? "." + ext.toLowerCase() : "");
    Path target = resolve(key);
    try {
      Files.createDirectories(target.getParent());
      long written = Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      return new StoredFile(key, written);
    } catch (IOException e) {
      throw new StorageException("文件写入失败: " + key, e);
    }
  }

  @Override
  public InputStream open(String key) {
    Path path = resolve(key);
    if (!Files.isRegularFile(path)) {
      throw new StorageException("文件不存在: " + key);
    }
    try {
      return Files.newInputStream(path);
    } catch (IOException e) {
      throw new StorageException("文件读取失败: " + key, e);
    }
  }

  @Override
  public void delete(String key) {
    try {
      Files.deleteIfExists(resolve(key));
    } catch (IOException e) {
      // 物理删除失败不阻断记录删除（幂等容错），残留孤儿文件无引用不暴露
      throw new StorageException("文件删除失败: " + key, e);
    }
  }

  /**
   * key → 绝对路径，白名单字符校验防路径穿越：
   * 仅允许日期分桶/UUID/扩展名形态（字母数字、/、.、-、_），且解析后必须仍在存储根目录内。
   */
  private Path resolve(String key) {
    if (key == null || key.isBlank() || !key.matches("[A-Za-z0-9/._-]+") || key.contains("..")) {
      throw new StorageException("非法的文件存储键");
    }
    Path path = basePath.resolve(key).normalize();
    if (!path.startsWith(basePath)) {
      throw new StorageException("非法的文件存储键");
    }
    return path;
  }
}
