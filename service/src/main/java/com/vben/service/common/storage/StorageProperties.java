package com.vben.service.common.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件存储配置（vben.file.*）。
 * storage 选择实现（local/oss/minio），local.base-path 为本地存储根目录。
 */
@Data
@Component
@ConfigurationProperties(prefix = "vben.file")
public class StorageProperties {

  /** 存储实现标识：local（默认）| oss | minio */
  private String storage = "local";

  private final Local local = new Local();

  private final Minio minio = new Minio();

  @Data
  public static class Local {
    /** 本地存储根目录（相对服务运行目录或绝对路径） */
    private String basePath = "./files";
  }

  @Data
  public static class Minio {
    /** MinIO 服务地址，如 http://127.0.0.1:9000 */
    private String endpoint = "http://127.0.0.1:9000";
    /** 访问密钥 */
    private String accessKey = "";
    /** 私有密钥 */
    private String secretKey = "";
    /** 存储桶（需提前创建） */
    private String bucket = "vben";
  }
}
