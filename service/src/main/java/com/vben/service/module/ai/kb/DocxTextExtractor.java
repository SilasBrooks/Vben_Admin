package com.vben.service.module.ai.kb;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * docx 文本提取器（POI XWPF，设计 D5）：按段落遍历产出分段。
 *
 * <p>表格文本 MVP 不提取（仅正文段落）；损坏/伪造 docx 抛可读 IO 异常。
 */
@Component
public class DocxTextExtractor implements DocumentTextExtractor {

  @Override
  public boolean supports(String ext) {
    return "docx".equals(ext);
  }

  @Override
  public TextSegments segments(InputStream in) throws IOException {
    try {
      XWPFDocument doc = new XWPFDocument(in);
      return new DocxSegments(doc);
    } catch (IOException | RuntimeException e) {
      // POI 对非 OOXML 内容抛 NotOfficeXmlFileException 等运行时异常，统一包装为可读 IO 异常
      throw new IOException("docx 文件解析失败，文件可能已损坏", e);
    }
  }

  /** 段落分段迭代器 */
  private static final class DocxSegments implements TextSegments {

    private final XWPFDocument doc;
    private final Iterator<XWPFParagraph> paragraphs;
    private boolean closed;

    DocxSegments(XWPFDocument doc) {
      this.doc = doc;
      this.paragraphs = doc.getParagraphsIterator();
    }

    @Override
    public boolean hasNext() {
      return !closed && paragraphs.hasNext();
    }

    @Override
    public String next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      return paragraphs.next().getText();
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      try {
        doc.close();
      } catch (IOException ignored) {
        // 关闭失败不影响业务
      }
    }
  }
}
