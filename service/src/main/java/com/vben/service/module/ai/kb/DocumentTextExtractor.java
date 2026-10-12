package com.vben.service.module.ai.kb;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * 知识库文档文本提取器抽象（设计 D5）：按扩展名路由，流式产出文本分段。
 *
 * <p>分段粒度由实现决定（PDF 按页 / docx 按段落 / 纯文本按行窗口），
 * 消耗方逐段消费即可保持内存常数——不要求整篇文本同时在堆内。
 * 迭代器用毕必须 {@link TextSegments#close()} 释放底层资源（文档句柄/临时文件）。
 */
public interface DocumentTextExtractor {

  /** 是否支持该扩展名（小写） */
  boolean supports(String ext);

  /**
   * 打开文本分段迭代器。实现负责消费传入流；解析失败（文件损坏/格式不符）抛 IOException。
   */
  TextSegments segments(InputStream in) throws IOException;

  /** 文本分段迭代器：产出顺序文本段，close 释放底层资源（可重复调用，幂等） */
  interface TextSegments extends Iterator<String>, AutoCloseable {

    @Override
    void close();
  }
}
