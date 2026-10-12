package com.vben.service.module.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vben.service.common.BizException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 技能包解析器单测：安全三道闸（路径穿越/条目数/解压总量）+ SKILL.md 定位
 * （包根或唯一一级目录，缺失/多处拒绝）+ frontmatter 轻量解析。
 */
class SkillPackageParserTest {

  /** 构造内存 zip（条目名 → 内容字节） */
  private byte[] zip(Map<String, byte[]> files) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(bos)) {
      for (Map.Entry<String, byte[]> e : files.entrySet()) {
        zos.putNextEntry(new ZipEntry(e.getKey()));
        zos.write(e.getValue());
        zos.closeEntry();
      }
    }
    return bos.toByteArray();
  }

  private byte[] zip(Map.Entry<String, String>... files) throws IOException {
    Map<String, byte[]> map = new LinkedHashMap<>();
    for (Map.Entry<String, String> f : files) {
      map.put(f.getKey(), f.getValue().getBytes(StandardCharsets.UTF_8));
    }
    return zip(map);
  }

  private static final String SKILL_MD = """
      ---
      name: code-reviewer
      title: 代码评审
      description: 当用户需要评审代码时使用
      ---
      # 评审指引
      先看结构再看细节。
      """;

  // ------------------------------------------------------------------
  // 标准包解析
  // ------------------------------------------------------------------

  @Test
  void standardPackageParsesFrontmatterAndEntries() throws IOException {
    byte[] zip = zip(
        Map.entry("code-reviewer/SKILL.md", SKILL_MD),
        Map.entry("code-reviewer/references/spec.md", "# 规格"),
        Map.entry("code-reviewer/assets/logo.png", "\u0000PNG"));
    SkillPackageParser.Parsed parsed = SkillPackageParser.parse(new ByteArrayInputStream(zip));

    assertThat(parsed.name()).isEqualTo("code-reviewer");
    assertThat(parsed.title()).isEqualTo("代码评审");
    assertThat(parsed.description()).isEqualTo("当用户需要评审代码时使用");
    assertThat(parsed.content()).startsWith("# 评审指引");
    assertThat(parsed.entries()).extracting(SkillPackageParser.PackageEntry::path)
        .containsExactly("code-reviewer/SKILL.md", "code-reviewer/references/spec.md",
            "code-reviewer/assets/logo.png");
    assertThat(parsed.entries().get(1).size()).isEqualTo("# 规格".getBytes(StandardCharsets.UTF_8).length);
  }

  @Test
  void rootSkillMdAccepted() throws IOException {
    byte[] zip = zip(Map.entry("SKILL.md", SKILL_MD));
    assertThat(SkillPackageParser.parse(new ByteArrayInputStream(zip)).name())
        .isEqualTo("code-reviewer");
  }

  @Test
  void frontmatterQuotesStripped() throws IOException {
    String md = "---\nname: \"quoted-name\"\ndescription: '单引号描述'\n---\n正文";
    SkillPackageParser.Parsed parsed = SkillPackageParser.parse(
        new ByteArrayInputStream(zip(Map.entry("SKILL.md", md))));
    assertThat(parsed.name()).isEqualTo("quoted-name");
    assertThat(parsed.description()).isEqualTo("单引号描述");
    // 无 title 时交由服务层以 name 兜底
    assertThat(parsed.title()).isNull();
  }

  // ------------------------------------------------------------------
  // SKILL.md 定位
  // ------------------------------------------------------------------

  @Test
  void missingSkillMdRejected() throws IOException {
    byte[] zip = zip(Map.entry("readme.txt", "没有技能定义"));
    assertThatThrownBy(() -> SkillPackageParser.parse(new ByteArrayInputStream(zip)))
        .isInstanceOf(BizException.class);
  }

  @Test
  void multipleSkillMdRejected() throws IOException {
    byte[] zip = zip(
        Map.entry("SKILL.md", SKILL_MD),
        Map.entry("another/SKILL.md", SKILL_MD));
    assertThatThrownBy(() -> SkillPackageParser.parse(new ByteArrayInputStream(zip)))
        .isInstanceOf(BizException.class);
  }

  @Test
  void deepNestedSkillMdNotLocated() throws IOException {
    // 两级目录下的 SKILL.md 不符合「包根或唯一一级目录」约定，按缺失拒绝
    byte[] zip = zip(Map.entry("a/b/SKILL.md", SKILL_MD));
    assertThatThrownBy(() -> SkillPackageParser.parse(new ByteArrayInputStream(zip)))
        .isInstanceOf(BizException.class);
  }

  // ------------------------------------------------------------------
  // 安全三道闸
  // ------------------------------------------------------------------

  @Test
  void pathTraversalEntriesRejected() throws IOException {
    byte[] dotdot = zip(Map.entry("SKILL.md", SKILL_MD), Map.entry("../evil.sh", "x"));
    assertThatThrownBy(() -> SkillPackageParser.parse(new ByteArrayInputStream(dotdot)))
        .isInstanceOf(BizException.class);

    byte[] absolute = zip(Map.entry("SKILL.md", SKILL_MD), Map.entry("/etc/passwd", "x"));
    assertThatThrownBy(() -> SkillPackageParser.parse(new ByteArrayInputStream(absolute)))
        .isInstanceOf(BizException.class);
  }

  @Test
  void tooManyEntriesRejected() throws IOException {
    Map<String, byte[]> files = new LinkedHashMap<>();
    files.put("SKILL.md", SKILL_MD.getBytes(StandardCharsets.UTF_8));
    for (int i = 0; i <= SkillPackageParser.MAX_ENTRIES; i++) {
      files.put("f" + i + ".txt", new byte[] {'x'});
    }
    assertThatThrownBy(() -> SkillPackageParser.parse(new ByteArrayInputStream(zip(files))))
        .isInstanceOf(BizException.class);
  }

  @Test
  void oversizedDecompressionRejected() throws IOException {
    // 解压后超 20MB（zip 声明 size 可被伪造，按实际读取字节数累计）
    Map<String, byte[]> files = new LinkedHashMap<>();
    files.put("SKILL.md", SKILL_MD.getBytes(StandardCharsets.UTF_8));
    files.put("data/big.txt", "a".repeat(21 * 1024 * 1024).getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> SkillPackageParser.parse(new ByteArrayInputStream(zip(files))))
        .isInstanceOf(BizException.class);
  }

  // ------------------------------------------------------------------
  // frontmatter 校验
  // ------------------------------------------------------------------

  @Test
  void badFrontmatterRejected() throws IOException {
    // 无 frontmatter
    assertThatThrownBy(() -> SkillPackageParser.parse(
        new ByteArrayInputStream(zip(Map.entry("SKILL.md", "# 只有正文")))))
        .isInstanceOf(BizException.class);
    // 缺 description
    assertThatThrownBy(() -> SkillPackageParser.parse(
        new ByteArrayInputStream(zip(Map.entry("SKILL.md", "---\nname: x\n---\n正文")))))
        .isInstanceOf(BizException.class);
    // 起始 --- 无收尾
    assertThatThrownBy(() -> SkillPackageParser.parse(
        new ByteArrayInputStream(zip(Map.entry("SKILL.md", "---\nname: x\ndescription: y\n正文")))))
        .isInstanceOf(BizException.class);
  }

  @Test
  void textFileDetectionByExtension() {
    assertThat(SkillPackageParser.isTextFile("references/api.md")).isTrue();
    assertThat(SkillPackageParser.isTextFile("scripts/run.SH")).isTrue();
    assertThat(SkillPackageParser.isTextFile("assets/logo.png")).isFalse();
    assertThat(SkillPackageParser.isTextFile("data/blob")).isFalse();
  }
}
