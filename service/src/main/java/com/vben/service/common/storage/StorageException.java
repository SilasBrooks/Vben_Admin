package com.vben.service.common.storage;

/**
 * 存储层异常：文件不存在、IO 失败等。由 GlobalExceptionHandler 统一转 500/404。
 */
public class StorageException extends RuntimeException {

  public StorageException(String message) {
    super(message);
  }

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
