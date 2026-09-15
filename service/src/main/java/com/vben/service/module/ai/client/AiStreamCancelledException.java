package com.vben.service.module.ai.client;

/**
 * 用户主动取消（停止生成 / SSE 断开 / 发起新问题）的静默标记，
 * 编排层捕获后不再下发 error 事件。
 */
public class AiStreamCancelledException extends RuntimeException {
}
