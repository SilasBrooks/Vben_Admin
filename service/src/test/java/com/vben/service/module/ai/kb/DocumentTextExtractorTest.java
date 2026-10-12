package com.vben.service.module.ai.kb;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文本提取器单测：md/txt 直读分段、PDF 生成式样例按页解析、
 * docx 构造样例段落、损坏文件报错、扫描件（空页）产出空文本。
 */
class DocumentTextExtractorTest {

  private final PlainTextExtractor plain = new PlainTextExtractor();
  private final PdfTextExtractor pdf = new PdfTextExtractor();
  private final DocxTextExtractor docx = new DocxTextExtractor();

  private static List<String> readAll(DocumentTextExtractor.TextSegments segments) {
    List<String> out = new ArrayList<>();
    try (segments) {
      while (segments.hasNext()) {
        out.add(segments.next());
      }
    }
    return out;
  }

  // ------------------------------------------------------------------
  // md/txt 直读
  // ------------------------------------------------------------------

  @Test
  void plainTextProducesSegmentsPreservingContent() throws IOException {
    String text = "第一行\n第二行\n\n第三行";
    try (var in = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))) {
      List<String> segments = readAll(plain.segments(in));
      String joined = String.join("", segments);
      assertTrue(joined.contains("第一行"));
      assertTrue(joined.contains("第三行"));
    }
  }

  @Test
  void plainTextSplitsLargeContentAtLineBoundaries() throws IOException {
    String line = "甲".repeat(100) + "\n";
    String text = line.repeat(2000); // 约 200KB
    try (var in = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))) {
      List<String> segments = readAll(plain.segments(in));
      assertTrue(segments.size() > 1, "大文本应切出多段");
      assertEquals(text, String.join("", segments), "分段拼接必须无损");
    }
  }

  @Test
  void plainSupportsMdTxtMarkdownOnly() {
    assertTrue(plain.supports("md"));
    assertTrue(plain.supports("txt"));
    assertFalse(plain.supports("pdf"));
  }

  // ------------------------------------------------------------------
  // PDF 按页
  // ------------------------------------------------------------------

  /** 生成两页样例 PDF（每页一行文字） */
  private static byte[] samplePdf(String page1Text, String page2Text) throws IOException {
    try (PDDocument doc = new PDDocument()) {
      writePage(doc, page1Text);
      writePage(doc, page2Text);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    }
  }

  private static void writePage(PDDocument doc, String text) throws IOException {
    PDPage page = new PDPage(PDRectangle.A4);
    doc.addPage(page);
    try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
      cs.beginText();
      cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
      cs.newLineAtOffset(50, 700);
      cs.showText(text);
      cs.endText();
    }
  }

  @Test
  void pdfExtractsTextPerPage() throws IOException {
    byte[] data = samplePdf("Hello page one", "Hello page two");
    try (var in = new ByteArrayInputStream(data)) {
      List<String> segments = readAll(pdf.segments(in));
      assertEquals(2, segments.size(), "两页 PDF 应产出两段");
      assertTrue(segments.get(0).contains("Hello page one"));
      assertTrue(segments.get(1).contains("Hello page two"));
    }
  }

  @Test
  void scannedPdfYieldsBlankSegments() throws IOException {
    byte[] data = samplePdf("", ""); // 无文本层（模拟扫描件）
    try (var in = new ByteArrayInputStream(data)) {
      List<String> segments = readAll(pdf.segments(in));
      assertTrue(segments.stream().allMatch(String::isBlank), "扫描页不应产出有效文本");
    }
  }

  @Test
  void corruptPdfThrowsReadableError() {
    assertThrows(IOException.class, () -> {
      try (var in = new ByteArrayInputStream("not a pdf".getBytes(StandardCharsets.UTF_8))) {
        readAll(pdf.segments(in));
      }
    });
  }

  // ------------------------------------------------------------------
  // docx 按段落
  // ------------------------------------------------------------------

  @Test
  void docxExtractsParagraphs() throws IOException {
    byte[] data;
    try (XWPFDocument doc = new XWPFDocument()) {
      doc.createParagraph().createRun().setText("第一段落");
      doc.createParagraph().createRun().setText("第二段落");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.write(out);
      data = out.toByteArray();
    }
    try (var in = new ByteArrayInputStream(data)) {
      List<String> segments = readAll(docx.segments(in));
      assertEquals(2, segments.size());
      assertEquals("第一段落", segments.get(0));
      assertEquals("第二段落", segments.get(1));
    }
  }

  @Test
  void corruptDocxThrowsReadableError() {
    assertThrows(IOException.class, () -> {
      try (var in = new ByteArrayInputStream("not a docx".getBytes(StandardCharsets.UTF_8))) {
        readAll(docx.segments(in));
      }
    });
  }

  // ------------------------------------------------------------------
  // 路由
  // ------------------------------------------------------------------

  @Test
  void routerPicksExtractorByExtension() {
    KbExtractorRouter router = new KbExtractorRouter(List.of(plain, pdf, docx));
    assertTrue(router.route("md") instanceof PlainTextExtractor);
    assertTrue(router.route("pdf") instanceof PdfTextExtractor);
    assertTrue(router.route("docx") instanceof DocxTextExtractor);
  }
}
