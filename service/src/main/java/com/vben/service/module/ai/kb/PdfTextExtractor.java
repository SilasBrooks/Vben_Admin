package com.vben.service.module.ai.kb;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.NoSuchElementException;

/**
 * PDF 文本提取器（PDFBox 3，设计 D5）：按页流式提取，内存占用与页数无关。
 *
 * <p>PDFBox 大文件加载需要随机访问文件（RandomAccessReadBufferedFile），
 * 先把上游流暂存到临时文件再加载（流式拷贝），段=单页文本；
 * 迭代结束或 close 时释放文档句柄并删除临时文件。
 */
@Component
public class PdfTextExtractor implements DocumentTextExtractor {

  @Override
  public boolean supports(String ext) {
    return "pdf".equals(ext);
  }

  @Override
  public TextSegments segments(InputStream in) throws IOException {
    Path temp = Files.createTempFile("kb-pdf-", ".pdf");
    try {
      Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
      PDDocument doc = Loader.loadPDF(temp.toFile());
      return new PdfSegments(doc, temp);
    } catch (IOException e) {
      Files.deleteIfExists(temp);
      if (e instanceof java.io.EOFException) {
        throw new IOException("PDF 文件不完整或已损坏", e);
      }
      throw new IOException("PDF 文件解析失败，文件可能已损坏或加密", e);
    }
  }

  /** 单页分段迭代器：每次产出当前页文本，翻页惰性进行 */
  private static final class PdfSegments implements TextSegments {

    private final PDDocument doc;
    private final Path temp;
    private int nextPage = 1;
    private boolean closed;

    PdfSegments(PDDocument doc, Path temp) {
      this.doc = doc;
      this.temp = temp;
    }

    @Override
    public boolean hasNext() {
      return !closed && nextPage <= doc.getNumberOfPages();
    }

    @Override
    public String next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      try {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(nextPage);
        stripper.setEndPage(nextPage);
        nextPage++;
        return stripper.getText(doc);
      } catch (IOException e) {
        throw new PlainTextExtractor.UncheckedIOExceptionSafe("PDF 第 " + nextPage + " 页文本提取失败", e);
      }
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
      try {
        Files.deleteIfExists(temp);
      } catch (IOException ignored) {
        // 临时文件残留由系统临时目录清理兜底
      }
    }
  }
}
