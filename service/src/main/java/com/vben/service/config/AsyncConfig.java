package com.vben.service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 知识库摄取线程池与调度配置：文档处理管线专用单 worker 线程池。
 *
 * <p>
 * 单 worker 串行消费，天然避免并发写同一文档与上游限流叠加；
 * 队列有界（默认容量），打满时上传接口快速失败提示稍后再试。
 * 管线投递由 {@code KbPipelineExecutor} 手动管理（维护执行中文档集合供重启恢复判定）；
 * 
 * @EnableScheduling 供分片会话清理与重启恢复扫描使用。
 */
@EnableScheduling
@Configuration
public class AsyncConfig {

  /** 知识库摄取线程池 Bean 名（{@code KbPipelineExecutor} 构造注入后手动 execute 投递） */
  public static final String KB_INGEST_EXECUTOR = "kbIngestExecutor";

  @Bean(name = KB_INGEST_EXECUTOR)
  public ThreadPoolTaskExecutor kbIngestExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("kb-ingest-");
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(50);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.initialize();
    return executor;
  }
}
