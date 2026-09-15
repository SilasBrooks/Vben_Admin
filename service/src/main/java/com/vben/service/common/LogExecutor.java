package com.vben.service.common;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 日志异步落库专用线程池（单线程守护 + 队列满时丢弃新任务）。
 *
 * <p>设计约束：日志写入绝不阻塞业务、绝不抛错影响业务——
 * 队列满（极端高峰）丢弃新日志，任务内异常静默吞掉仅打 debug 日志。
 */
@Slf4j
@Component
public class LogExecutor {

  private static final int QUEUE_CAPACITY = 1000;

  private final ExecutorService executor = new ThreadPoolExecutor(
      1, 1, 60L, TimeUnit.SECONDS,
      new LinkedBlockingQueue<>(QUEUE_CAPACITY),
      r -> {
        Thread t = new Thread(r, "async-log-writer");
        t.setDaemon(true);
        return t;
      },
      // 队列满时丢弃新日志，保业务响应
      new ThreadPoolExecutor.DiscardPolicy());

  /** 提交异步日志任务（静默吞异常） */
  public void submit(Runnable task) {
    executor.submit(() -> {
      try {
        task.run();
      } catch (Exception e) {
        log.debug("日志异步写入失败(不影响业务): {}", e.getMessage());
      }
    });
  }

  @PreDestroy
  public void shutdown() {
    executor.shutdown();
  }
}
