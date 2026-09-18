package com.vben.service.common.storage;

import com.vben.service.common.BizException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * MinIO 对象存储实现：与本地磁盘实现同 key 格式（yyyy/MM/dd/{uuid}.{ext}），业务无感切换。
 *
 * <p>启动时不主动探测 bucket（避免本地无 MinIO 时起不来）；bucket 需提前创建。
 * delete 对不存在的对象幂等容错（NoSuchKey 静默）。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "vben.file.storage", havingValue = "minio")
public class MinioStorageService implements StorageService {

  private static final DateTimeFormatter BUCKET = DateTimeFormatter.ofPattern("yyyy/MM/dd");

  private final MinioClient client;
  private final String bucket;

  public MinioStorageService(StorageProperties properties) {
    StorageProperties.Minio conf = properties.getMinio();
    this.client = MinioClient.builder()
        .endpoint(conf.getEndpoint())
        .credentials(conf.getAccessKey(), conf.getSecretKey())
        .build();
    this.bucket = conf.getBucket();
    ensureBucket();
  }

  /** 启动时确保 bucket 存在（幂等）；MinIO 不可达仅告警不阻断启动，首次读写时再暴露错误 */
  private void ensureBucket() {
    try {
      boolean exists = client.bucketExists(
          io.minio.BucketExistsArgs.builder().bucket(bucket).build());
      if (!exists) {
        client.makeBucket(io.minio.MakeBucketArgs.builder().bucket(bucket).build());
        log.info("MinIO bucket 已创建: {}", bucket);
      }
    } catch (Exception e) {
      log.warn("MinIO 预检失败（服务未就绪？），将在首次读写时报错: {}", e.getMessage());
    }
  }

  @Override
  public StoredFile store(InputStream in, String originalName, long size) {
    String ext = StringUtils.getFilenameExtension(originalName);
    String key = LocalDate.now().format(BUCKET) + "/" + UUID.randomUUID()
        + (ext != null ? "." + ext.toLowerCase() : "");
    try {
      client.putObject(PutObjectArgs.builder()
          .bucket(bucket)
          .object(key)
          // 已知 size 直传，SDK 单次上传（文件上限 10MB，无需分片）
          .stream(in, size, -1)
          .build());
      return new StoredFile(key, size);
    } catch (Exception e) {
      throw new StorageException("文件写入失败(对象存储): " + key + "，请确认 bucket 已创建", e);
    }
  }

  @Override
  public InputStream open(String key) {
    try {
      return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
    } catch (ErrorResponseException e) {
      if ("NoSuchKey".equals(e.errorResponse().code())) {
        throw new StorageException("文件不存在: " + key);
      }
      throw new StorageException("文件读取失败(对象存储): " + key, e);
    } catch (Exception e) {
      throw new StorageException("文件读取失败(对象存储): " + key, e);
    }
  }

  @Override
  public void delete(String key) {
    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
    } catch (ErrorResponseException e) {
      if ("NoSuchKey".equals(e.errorResponse().code())) {
        // 对象已不存在，幂等成功
        return;
      }
      throw new StorageException("文件删除失败(对象存储): " + key, e);
    } catch (Exception e) {
      throw new StorageException("文件删除失败(对象存储): " + key, e);
    }
  }
}
