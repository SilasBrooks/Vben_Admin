package com.vben.service.module.ai.client;

/**
 * 上游大模型调用异常，message 始终为可直接展示给用户的中文提示。
 */
public class AiUpstreamException extends RuntimeException {

  public AiUpstreamException(String message) {
    super(message);
  }

  public AiUpstreamException(String message, Throwable cause) {
    super(message, cause);
  }
}
