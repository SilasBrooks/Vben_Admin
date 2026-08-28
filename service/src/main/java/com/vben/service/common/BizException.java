package com.vben.service.common;

import lombok.Getter;

/**
 * 业务异常。code 同时作为 HTTP 状态码返回（401/403/400/500），
 * 与前端 mock 的 unAuthorizedResponse/forbiddenResponse 行为一致。
 */
@Getter
public class BizException extends RuntimeException {

  private final int status;

  public BizException(int status, String message) {
    super(message);
    this.status = status;
  }

  /** 401：未认证 / token 失效 */
  public static BizException unauthorized(String message) {
    return new BizException(401, message);
  }

  /** 403：已认证但无权限（登录失败也用它，与 mock 的 forbiddenResponse 对齐） */
  public static BizException forbidden(String message) {
    return new BizException(403, message);
  }

  /** 400：参数错误 */
  public static BizException badRequest(String message) {
    return new BizException(400, message);
  }
}
