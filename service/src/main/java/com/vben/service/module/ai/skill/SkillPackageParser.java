package com.vben.service.module.ai.skill;

import com.vben.service.common.BizException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 技能包（zip）解析器：安全校验 + SKILL.md frontmatter 轻量解析。
 *
 * <p>安全三道闸（任一失败整体拒绝，调用方保证不落库不落存储）：
 * <ol>
 *   <li>路径安全：条目名规范化后不得含 {@code ..} 段、不得为绝对路径（防路径穿越）</li>
 *   <li>体量上限：条目数 ≤{@link #MAX_ENTRIES}、解压总量 ≤{@link #MAX_TOTAL_BYTES}
 *       —— 按实际读取字节数累计（不信任 zip 声明 size，防 zip bomb 伪造声明）</li>
 *   <li>SKILL.md 定位：包根（{@code SKILL.md}）或唯一一级目录（{@code <dir>/SKILL.md}），
 *       缺失或多处存在均拒绝</li>
 * </ol>
 *
 * <p>frontmatter 手写轻量解析（不引入 snakeyaml 依赖）：{@code ---} 起止行内
 * name/description/title 平铺 KV（容忍包裹引号），正文取结束 {@code ---} 之后内容。
 */
public final class SkillPackageParser {

  /** zip 条目数上限 */
  public static final int MAX_ENTRIES = 200;

  /** 解压后总大小上限：20MB */
  public static final long MAX_TOTAL_BYTES = 20L * 1024 * 1024;

  /** 文本文件扩展名白名单（load_skill_file 可加载 / 清单标记 text） */
  public static final Set<String> TEXT_EXTENSIONS = Set.of(
      ".css", ".csv", ".conf", ".gitignore", ".htm", ".html", ".ini", ".java", ".js",
      ".json", ".jsx", ".log", ".markdown", ".md", ".properties", ".py", ".sh", ".sql",
      ".toml", ".ts", ".tsx", ".txt", ".vue", ".xml", ".yaml", ".yml");

  private SkillPackageParser() {
  }

  /** 包内条目（清单展示用）：path 为规范化相对路径，size 为实际字节数 */
  public record PackageEntry(String path, long size) {
  }

  /** 解析结果：frontmatter 字段与正文 + 包内全部条目（含 SKILL.md 自身） */
  public record Parsed(String name, String title, String description, String content,
                       List<PackageEntry> entries) {
  }

  /**
   * 解析技能包 zip 流：三道闸校验 + SKILL.md frontmatter 提取。
   * 流被完整消费；任何校验失败抛 BizException（调用方不落库不落存储）。
   */
  public static Parsed parse(InputStream in) {
    String skillMdPath = null;
    byte[] skillMdBytes = null;
    List<PackageEntry> entries = new ArrayList<>();
    long total = 0;
    int count = 0;
    try (ZipInputStream zis = new ZipInputStream(in)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String path = normalizePath(entry.getName());
        requireSafePath(entry.getName());
        if (++count > MAX_ENTRIES) {
          throw BizException.badRequest("error.ai.skillPkg.tooManyEntries", MAX_ENTRIES);
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        long entrySize = drain(zis, buf);
        total += entrySize;
        if (total > MAX_TOTAL_BYTES) {
          throw BizException.badRequest("error.ai.skillPkg.tooLarge", MAX_TOTAL_BYTES / 1024 / 1024);
        }
        entries.add(new PackageEntry(path, entrySize));
        // SKILL.md 只认包根或唯一一级目录两个位置，深层同名文件不参与定位
        if (path.equals("SKILL.md") || path.matches("^[^/]+/SKILL\\.md$")) {
          if (skillMdPath != null) {
            throw BizException.badRequest("error.ai.skillPkg.multiSkillMd");
          }
          skillMdPath = path;
          skillMdBytes = buf.toByteArray();
        }
      }
    } catch (IOException e) {
      throw BizException.badRequest("error.ai.skillPkg.zipRead");
    }
    if (skillMdBytes == null) {
      throw BizException.badRequest("error.ai.skillPkg.missingSkillMd");
    }
    return parseSkillMd(new String(skillMdBytes, StandardCharsets.UTF_8), entries);
  }

  /** 是否文本文件（按扩展名白名单判定，大小写不敏感） */
  public static boolean isTextFile(String path) {
    String lower = path.toLowerCase(Locale.ROOT);
    return TEXT_EXTENSIONS.stream().anyMatch(lower::endsWith);
  }

  /**
   * 读取流并统计实际字节数（zip bomb 防护不信任声明 size）。
   * sink 为 null 时仅丢弃内容计数（清点既有包用）。
   */
  static long drain(InputStream in, ByteArrayOutputStream sink) throws IOException {
    byte[] tmp = new byte[8192];
    long size = 0;
    int n;
    while ((n = in.read(tmp)) != -1) {
      size += n;
      if (size > MAX_TOTAL_BYTES) {
        throw BizException.badRequest("error.ai.skillPkg.tooLarge", MAX_TOTAL_BYTES / 1024 / 1024);
      }
      if (sink != null) {
        sink.write(tmp, 0, n);
      }
    }
    return size;
  }

  /** 路径穿越防护：拒绝 .. 段、绝对路径（/ 开头或 Windows 盘符）；统一斜杠后返回 */
  static String requireSafePath(String raw) {
    String path = normalizePath(raw);
    if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")
        || java.util.Arrays.stream(path.split("/")).anyMatch(".."::equals)) {
      throw BizException.badRequest("error.ai.skillPkg.pathTraversal", raw);
    }
    return path;
  }

  /** 条目路径规范化：反斜杠统一为正斜杠、去掉首尾空白 */
  static String normalizePath(String raw) {
    return raw.replace('\\', '/').trim();
  }

  /**
   * frontmatter 轻量解析：首行必须是 {@code ---}，其后的 name/description/title KV
   * 以第二个 {@code ---} 行收尾；正文取其后内容 trim（空正文交由服务层 required 校验拦截）。
   */
  private static Parsed parseSkillMd(String text, List<PackageEntry> entries) {
    String content = text.startsWith("\uFEFF") ? text.substring(1) : text;
    String[] lines = content.split("\n", -1);
    if (!lines[0].trim().equals("---")) {
      throw BizException.badRequest("error.ai.skillPkg.badFrontmatter");
    }
    String name = null;
    String title = null;
    String description = null;
    int closing = -1;
    for (int i = 1; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.equals("---")) {
        closing = i;
        break;
      }
      int colon = line.indexOf(':');
      if (colon <= 0) {
        continue;
      }
      String key = line.substring(0, colon).trim();
      String value = stripQuotes(line.substring(colon + 1).trim());
      switch (key) {
        case "name" -> name = value;
        case "title" -> title = value;
        case "description" -> description = value;
        default -> {
          // frontmatter 其他键（license/allowed-tools 等）忽略
        }
      }
    }
    if (closing < 0 || name == null || name.isBlank()
        || description == null || description.isBlank()) {
      throw BizException.badRequest("error.ai.skillPkg.badFrontmatter");
    }
    StringBuilder body = new StringBuilder();
    for (int i = closing + 1; i < lines.length; i++) {
      body.append(lines[i]).append('\n');
    }
    return new Parsed(name.trim(), title, description, body.toString().trim(), entries);
  }

  /** 去掉包裹值的成对引号（YAML 常见写法 "..." / '...'） */
  private static String stripQuotes(String value) {
    if (value.length() >= 2
        && ((value.startsWith("\"") && value.endsWith("\""))
        || (value.startsWith("'") && value.endsWith("'")))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }
}
