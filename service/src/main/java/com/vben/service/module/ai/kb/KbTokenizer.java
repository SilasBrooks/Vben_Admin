package com.vben.service.module.ai.kb;

/**
 * 知识库全文检索分词器：应用侧实现，不依赖数据库扩展。
 *
 * <p>中文按相邻两字（bigram）切分（孤立单字降级为单字 token），
 * 英文与数字连续序列按词切分；token 以空格连接后经
 * {@code to_tsvector('simple', ...)} 入库，查询侧经 {@code plainto_tsquery} 匹配。
 * 入库与查询 MUST 使用同一分词器，保证词形一致。
 */
public final class KbTokenizer {

  /** 单次分词最大 token 数（超限截断：超长查询 AND 链必然零命中，截断保召回） */
  static final int MAX_TOKENS = 16;

  private KbTokenizer() {
  }

  /**
   * 分词并输出空格连接的 token 串；无有效 token（纯标点/空白）返回空串。
   * 调用方以空串判定退化为纯向量检索。
   */
  public static String tokenize(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    StringBuilder out = new StringBuilder();
    int count = 0;
    int i = 0;
    int len = text.length();
    while (i < len && count < MAX_TOKENS) {
      char c = text.charAt(i);
      if (isCjk(c)) {
        // 中文连续段：滑动窗口 bigram（步长 1，段长 n 产 n-1 个 token）——
        // 非重叠窗口会让查询词跨切分相位漏召回（如文档「系统接入」切「系统/统接」
        // 时查询「接入」永不命中），滑窗保证任意两字组合都成 token
        int j = i;
        while (j < len && isCjk(text.charAt(j))) {
          j++;
        }
        for (int p = i; p + 1 < j && count < MAX_TOKENS; p++, count++) {
          appendToken(out, text.substring(p, p + 2));
        }
        // 段长恰为 1（孤立中文字符）时保留单字，避免单字查询零命中
        if (j - i == 1 && count < MAX_TOKENS) {
          appendToken(out, text.substring(i, j));
          count++;
        }
        i = j;
      } else if (Character.isLetterOrDigit(c)) {
        // 英文/数字连续序列按词
        int j = i;
        while (j < len && Character.isLetterOrDigit(text.charAt(j)) && !isCjk(text.charAt(j))) {
          j++;
        }
        appendToken(out, text.substring(i, j).toLowerCase());
        count++;
        i = j;
      } else {
        i++;
      }
    }
    return out.toString();
  }

  private static void appendToken(StringBuilder out, String token) {
    if (out.length() > 0) {
      out.append(' ');
    }
    out.append(token);
  }

  /** CJK 统一表意文字（含扩展 A）+ 兼容表意区 */
  private static boolean isCjk(char c) {
    return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF) || (c >= 0xF900 && c <= 0xFAFF);
  }
}
