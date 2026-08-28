package com.vben.service.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应体，与前端 @vben/request 的 defaultResponseInterceptor 约定一致：
 * <pre>
 *   code === 0 视为成功，取 data 字段；否则抛错提示 message
 * </pre>
 * 结构与前端 backend-mock 的 useResponseSuccess/useResponseError 完全对齐。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record R<T>(int code, T data, Object error, String message) {

  public static final int SUCCESS = 0;
  public static final int FAIL = -1;

  public static <T> R<T> ok(T data) {
    return new R<>(SUCCESS, data, null, "ok");
  }

  public static R<Void> ok() {
    return new R<>(SUCCESS, null, null, "ok");
  }

  public static R<Void> fail(String message) {
    return new R<>(FAIL, null, message, message);
  }

  public static R<Void> fail(Object error, String message) {
    return new R<>(FAIL, null, error, message);
  }
}
