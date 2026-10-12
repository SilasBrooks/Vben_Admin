package com.vben.service.module.ai.kb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 知识库切块器单测：Markdown 标题切分、纯文本段落聚合、超长块二次切分（边界优先 + 重叠）。
 */
class KbTextChunkerTest {

  // ------------------------------------------------------------------
  // Markdown：按标题切主块，标题行归入所属块
  // ------------------------------------------------------------------

  @Test
  void markdownSplitsByHeadings() {
    String text = """
        # 安装指南
        先安装依赖，再初始化配置。

        ## 常见问题
        启动失败先检查端口占用。
        """;
    List<String> chunks = KbTextChunker.chunk(text, true);

    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0)).startsWith("# 安装指南").contains("初始化配置");
    assertThat(chunks.get(1)).startsWith("## 常见问题").contains("端口占用");
  }

  @Test
  void markdownKeepsNonHeadingContentTogether() {
    List<String> chunks = KbTextChunker.chunk("第一行\n第二行", true);
    assertThat(chunks).containsExactly("第一行\n第二行");
  }

  // ------------------------------------------------------------------
  // 纯文本：按空行段落聚合
  // ------------------------------------------------------------------

  @Test
  void plainTextAggregatesParagraphs() {
    List<String> chunks = KbTextChunker.chunk("段落一内容。\n\n段落二内容。\n\n段落三内容。", false);
    assertThat(chunks).hasSize(1);
    assertThat(chunks.get(0)).contains("段落一").contains("段落二").contains("段落三");
  }

  @Test
  void plainTextFlushesWhenAggregationReachesLimit() {
    String paraA = "甲".repeat(500);
    String paraB = "乙".repeat(500);
    List<String> chunks = KbTextChunker.chunk(paraA + "\n\n" + paraB, false);

    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0)).contains("甲").doesNotContain("乙");
    assertThat(chunks.get(1)).contains("乙");
  }

  // ------------------------------------------------------------------
  // 超长块二次切分
  // ------------------------------------------------------------------

  @Test
  void overlongParagraphIsHardSplitWithOverlap() {
    String text = "字".repeat(2000);
    List<String> chunks = KbTextChunker.chunk(text, false);

    assertThat(chunks).hasSize(3);
    for (String chunk : chunks) {
      assertThat(chunk.length()).isLessThanOrEqualTo(KbTextChunker.MAX_CHUNK_CHARS);
    }
    // 无换行边界：按定长窗口硬切，相邻窗口保留 OVERLAP_CHARS 字符重叠
    assertThat(chunks.get(0)).hasSize(KbTextChunker.MAX_CHUNK_CHARS);
    assertThat(chunks.get(1).substring(0, KbTextChunker.OVERLAP_CHARS))
        .isEqualTo(chunks.get(0).substring(chunks.get(0).length() - KbTextChunker.OVERLAP_CHARS));
  }

  @Test
  void overlongBlockPrefersLineBoundary() {
    // 第一窗口（0-800）在 700 处有换行 → 在换行处断开而非硬切 800
    String text = "甲".repeat(700) + "\n" + "乙".repeat(1500);
    List<String> chunks = KbTextChunker.chunk(text, false);

    assertThat(chunks.get(0)).isEqualTo("甲".repeat(700));
    for (String chunk : chunks) {
      assertThat(chunk.length()).isLessThanOrEqualTo(KbTextChunker.MAX_CHUNK_CHARS);
    }
  }

  // ------------------------------------------------------------------
  // 边界
  // ------------------------------------------------------------------

  @Test
  void blankInputYieldsNoChunks() {
    assertThat(KbTextChunker.chunk("  \n\n  ", true)).isEmpty();
    assertThat(KbTextChunker.chunk("   ", false)).isEmpty();
  }
}
