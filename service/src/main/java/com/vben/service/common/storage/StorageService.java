package com.vben.service.common.storage;

import java.io.InputStream;

/**
 * 存储抽象：业务只依赖本接口与 {@link StoredFile}，不感知具体实现（本地磁盘 / OSS / MinIO）。
 * 实现由 {@code vben.file.storage} 配置选择（@ConditionalOnProperty 装配）。
 */
public interface StorageService {

  /**
   * 存储一个文件。原始文件名仅用于推导扩展名，存储名由实现随机生成。
   *
   * @param in          文件内容流（调用方负责关闭）
   * @param originalName 原始文件名（用于保留扩展名）
   * @return 存储结果（key + 实际字节数）
   */
  StoredFile store(InputStream in, String originalName);

  /**
   * 打开指定 key 的文件读取流。文件不存在时抛出 {@link StorageException}。
   */
  InputStream open(String key);

  /**
   * 删除指定 key 的文件。文件不存在时应静默容错（幂等），不抛异常。
   */
  void delete(String key);
}
