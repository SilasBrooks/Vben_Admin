package com.vben.service.module.ai.kb;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全文检索分词器单测：中文 bigram、英文/数字按词、中英混合、孤立单字降级、
 * 纯符号空串、token 上限截断。
 */
class KbTokenizerTest {

  @Test
  void chineseTextIsSplitIntoBigrams() {
    assertThat(KbTokenizer.tokenize("系统接入"))
        .isEqualTo("系统 统接 接入");
  }

  @Test
  void oddLengthChineseDropsTailSingleChar() {
    // 3 字滑窗产生 2 个 bigram，全部两字组合可被查询精确命中
    assertThat(KbTokenizer.tokenize("接入中"))
        .isEqualTo("接入 入中");
  }

  @Test
  void isolatedChineseCharKeptAsSingleToken() {
    assertThat(KbTokenizer.tokenize("接")).isEqualTo("接");
  }

  @Test
  void englishAndDigitsAreWordTokens() {
    assertThat(KbTokenizer.tokenize("Vben-Admin 2026"))
        .isEqualTo("vben admin 2026");
  }

  @Test
  void mixedContentKeepsBothSides() {
    assertThat(KbTokenizer.tokenize("部署 Vben"))
        .isEqualTo("部署 vben");
  }

  @Test
  void symbolOnlyQueryReturnsEmpty() {
    assertThat(KbTokenizer.tokenize("!!!？？")).isEmpty();
    assertThat(KbTokenizer.tokenize("   ")).isEmpty();
    assertThat(KbTokenizer.tokenize(null)).isEmpty();
  }

  @Test
  void tokensAreCappedAtLimit() {
    // 6 字滑窗 → 5 个 bigram（不触顶）
    assertThat(KbTokenizer.tokenize("一二三四五六").split(" ").length).isEqualTo(5);
    // 20 字滑窗 → 19 个 bigram，超上限截断为 16
    assertThat(KbTokenizer.tokenize("一二三四五六七八九十甲乙丙丁戊己庚辛壬癸").split(" ").length)
        .isEqualTo(16);
  }
}
