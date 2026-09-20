package com.vben.service.common;

import lombok.Getter;

/**
 * 业务异常。code 同时作为 HTTP 状态码返回（401/403/400/409/429/500）。
 *
 * <p>静态工厂方法的第一个参数是 messages.properties 的消息 key（后续为占位符参数），
 * 抛出点即按当前请求语言（Accept-Language）解析为最终文案存入 message——
 * 异常消费方（全局异常处理器、AI 工具回传、操作日志）拿到的都是已翻译文案。
 * 直接 new 的场景（幂等切面等）传入的应是已解析文案。
 */
@Getter
public class BizException extends RuntimeException {

  private final int status;

  public BizException(int status, String message) {
    super(message);
    this.status = status;
  }

  private static BizException of(int status, String code, Object... args) {
    return new BizException(status, I18nMessage.get(code, args));
  }

  /** 401：未认证 / token 失效 */
  public static BizException unauthorized(String code, Object... args) {
    return of(401, code, args);
  }

  /** 403：已认证但无权限（登录失败也用它，与 mock 的 forbiddenResponse 对齐） */
  public static BizException forbidden(String code, Object... args) {
    return of(403, code, args);
  }

  /** 400：参数错误 */
  public static BizException badRequest(String code, Object... args) {
    return of(400, code, args);
  }

  /** 429：请求过于频繁（限流 / 登录锁定） */
  public static BizException tooManyRequests(String code, Object... args) {
    return of(429, code, args);
  }

  /** 409：重复提交（幂等窗口内拒绝重复请求） */
  public static BizException conflict(String code, Object... args) {
    return of(409, code, args);
  }
}
