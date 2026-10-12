package com.vben.service.module.ai.kb;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * 纯文本提取器（md/txt/markdown）：按行窗口流式产出分段。
 *
 * <p>每段累积约 {@link #SEGMENT_CHARS} 字符后在行边界切出（保留换行），
 * 大文件内存常数；Markdown 标题切块仅对「旧路径全文在内存」的小文件生效
 * （设计 D5：分片路径的提取文本按长度窗口切分）。
 */
@Component
public class PlainTextExtractor implements DocumentTextExtractor {

  /** 单段字符数目标（约 64K，行边界对齐） */
  static final int SEGMENT_CHARS = 64 * 1024;

  private static final Set<String> EXTENSIONS = Set.of("md", "txt", "markdown");

  @Override
  public boolean supports(String ext) {
    return EXTENSIONS.contains(ext);
  }

  @Override
  public TextSegments segments(InputStream in) {
    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    return new TextSegments() {
      private String next;
      private boolean exhausted;
      private boolean closed;

      @Override
      public boolean hasNext() {
        if (closed) {
          return false;
        }
        if (next == null && !exhausted) {
          next = readSegment();
          if (next == null) {
            exhausted = true;
            close();
          }
        }
        return next != null;
      }

      @Override
      public String next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        String result = next;
        next = null;
        return result;
      }

      /** 按行累积至目标字符数后整段返回（返回 null 表示流结束） */
      private String readSegment() {
        StringBuilder sb = new StringBuilder(SEGMENT_CHARS + 1024);
        try {
          String line;
          while (sb.length() < SEGMENT_CHARS && (line = reader.readLine()) != null) {
            sb.append(line).append('\n');
          }
        } catch (IOException e) {
          throw new UncheckedIOExceptionSafe("读取文本内容失败", e);
        }
        return sb.isEmpty() ? null : sb.toString();
      }

      @Override
      public void close() {
        if (!closed) {
          closed = true;
          try {
            reader.close();
          } catch (IOException ignored) {
            // 关闭失败不影响业务
          }
        }
      }
    };
  }

  /** IOException 包装为非受检异常，供迭代器内抛出（管线统一兜底转失败原因） */
  static final class UncheckedIOExceptionSafe extends RuntimeException {

    UncheckedIOExceptionSafe(String message, IOException cause) {
      super(message, cause);
    }
  }
}
