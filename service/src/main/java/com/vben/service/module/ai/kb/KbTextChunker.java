package com.vben.service.module.ai.kb;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库文本切块器（纯函数，无状态）。
 *
 * <p>策略（设计 D4）：
 * <ul>
 *   <li>Markdown：按 1-3 级标题行切主块（标题行保留在块头）；</li>
 *   <li>纯文本：按空行段落聚合；</li>
 *   <li>任何超 {@link #MAX_CHUNK_CHARS} 的块再按段落边界二次切，
 *       仍超长按窗口硬切，相邻窗口保留 {@link #OVERLAP_CHARS} 字符重叠保上下文。</li>
 * </ul>
 * 全程按 Java char 计数（中文友好），与 embedding 分词器无关，只求语义块尽量完整。
 */
public final class KbTextChunker {

  /** 单块字符上限 */
  public static final int MAX_CHUNK_CHARS = 800;

  /** 硬切窗口重叠字符数 */
  public static final int OVERLAP_CHARS = 80;

  private KbTextChunker() {
  }

  /**
   * 切块入口：返回顺序切块列表（空行块已剔除）。
   *
   * @param text     文档正文
   * @param markdown 是否按 Markdown 处理（md 按标题切，txt 按段落聚合）
   */
  public static List<String> chunk(String text, boolean markdown) {
    List<String> sections = markdown ? splitByHeadings(text) : splitByParagraphs(text);
    List<String> out = new ArrayList<>();
    for (String section : sections) {
      String trimmed = section.strip();
      if (trimmed.isEmpty()) {
        continue;
      }
      if (trimmed.length() <= MAX_CHUNK_CHARS) {
        out.add(trimmed);
      } else {
        out.addAll(hardSplit(trimmed));
      }
    }
    return out;
  }

  /**
   * 存储路径分段切块（提取器产出的单段）：段内超上限走窗口二次切分，
   * 保留 {@link #OVERLAP_CHARS} 重叠；段本身已按页/段落/行窗口预切，无需跨段处理。
   */
  public static List<String> chunkSegment(String segment) {
    String trimmed = segment.strip();
    if (trimmed.isEmpty()) {
      return List.of();
    }
    if (trimmed.length() <= MAX_CHUNK_CHARS) {
      return List.of(trimmed);
    }
    return hardSplit(trimmed);
  }

  /** Markdown 按标题行（# / ## / ### 开头）切主块，标题行归入所属块 */
  static List<String> splitByHeadings(String text) {
    List<String> sections = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String line : text.split("\n", -1)) {
      if (line.matches("^#{1,3}\\s.*") && current.length() > 0) {
        sections.add(current.toString());
        current = new StringBuilder();
      }
      current.append(line).append('\n');
    }
    if (current.length() > 0) {
      sections.add(current.toString());
    }
    return sections;
  }

  /** 纯文本按空行段落聚合到不超过单块上限（单独超长的段落交由二次切分） */
  static List<String> splitByParagraphs(String text) {
    List<String> out = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String para : text.split("\\n\\s*\\n")) {
      String trimmed = para.strip();
      if (trimmed.isEmpty()) {
        continue;
      }
      // 单段已超上限：先flush聚合中的内容，再让该段独立走二次切分
      if (trimmed.length() > MAX_CHUNK_CHARS) {
        if (current.length() > 0) {
          out.add(current.toString());
          current = new StringBuilder();
        }
        out.add(trimmed);
        continue;
      }
      if (current.length() + trimmed.length() + 2 > MAX_CHUNK_CHARS && current.length() > 0) {
        out.add(current.toString());
        current = new StringBuilder();
      }
      current.append(trimmed).append("\n\n");
    }
    if (current.length() > 0) {
      out.add(current.toString());
    }
    return out;
  }

  /**
   * 超长块二次切分：优先在窗口末尾附近的段落/换行边界断开，
   * 找不到边界按定长窗口硬切，相邻窗口重叠 {@link #OVERLAP_CHARS} 字符。
   */
  static List<String> hardSplit(String text) {
    List<String> out = new ArrayList<>();
    int start = 0;
    while (start < text.length()) {
      int end = Math.min(start + MAX_CHUNK_CHARS, text.length());
      if (end < text.length()) {
        // 在窗口后半段找最近的换行边界，避免切断句子
        int boundary = text.lastIndexOf('\n', end);
        if (boundary > start + MAX_CHUNK_CHARS / 2) {
          end = boundary + 1;
        }
      }
      out.add(text.substring(start, end).strip());
      if (end >= text.length()) {
        break;
      }
      start = Math.max(end - OVERLAP_CHARS, start + 1);
    }
    return out;
  }
}
