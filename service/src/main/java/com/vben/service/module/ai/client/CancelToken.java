package com.vben.service.module.ai.client;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流式请求取消令牌：用户点"停止生成"或 SSE 连接断开时置位，
 * 编排线程检测到后中断对上游的读取。
 */
public class CancelToken {

  private final AtomicBoolean cancelled = new AtomicBoolean(false);

  public void cancel() {
    cancelled.set(true);
  }

  public boolean isCancelled() {
    return cancelled.get();
  }
}
