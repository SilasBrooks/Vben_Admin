package com.vben.service.module.ai.skill;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vben.service.common.BizException;
import com.vben.service.common.storage.StorageService;
import com.vben.service.common.storage.StoredFile;
import com.vben.service.module.ai.skill.mapper.AgentSkillMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Agent 技能服务：CRUD + 启停 + 防膨胀约束 + 技能包上传/文件提取。
 *
 * <p>技能正文会注入模型上下文，按不可信输入设防：name 正则白名单、
 * description/content 长度上限、同时启用数量上限。内置技能（builtIn=1）
 * 可编辑可停用但不可删除。
 *
 * <p>技能包：上传 zip（安全校验 + frontmatter 解析）创建技能，原包整体入存储；
 * 文件清单/单文件读取按包内路径精确匹配只读提取（编辑走 DB content，包文件是
 * 来源归档与参考资料，两者允许分叉不做同步）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSkillService {

  /** name 格式：2-64 位小写字母/数字/连字符，字母或数字开头 */
  public static final String NAME_PATTERN = "^[a-z0-9][a-z0-9-]{1,63}$";

  /** 描述长度上限（字符） */
  public static final int MAX_DESCRIPTION_CHARS = 300;

  /** 正文长度上限（字符） */
  public static final int MAX_CONTENT_CHARS = 8000;

  /** 同时启用技能数量上限 */
  public static final int MAX_ENABLED_SKILLS = 20;

  /** 技能包 zip 上传大小上限：10MB */
  public static final long MAX_PACKAGE_BYTES = 10L * 1024 * 1024;

  /** AI 工具读取文本文件的内容上限：64KB（超出截断附提示） */
  public static final int MAX_AI_FILE_CHARS = 64 * 1024;

  private final AgentSkillMapper mapper;
  private final StorageService storageService;

  /** 分页列表：名称/标题模糊过滤，时间倒序 */
  public IPage<AgentSkill> page(long pageNo, long pageSize, String keyword) {
    QueryWrapper<AgentSkill> q = new QueryWrapper<>();
    if (StringUtils.hasText(keyword)) {
      q.and(w -> w.like("name", keyword).or().like("title", keyword));
    }
    q.orderByDesc("create_time");
    return mapper.selectPage(new Page<>(pageNo, pageSize), q);
  }

  /** 新增：校验 + name 唯一；enabled 缺省停用 */
  public AgentSkill create(AgentSkill skill) {
    validate(skill);
    requireNameFree(skill.getName(), null);
    skill.setBuiltIn(0);
    if (skill.getEnabled() == null) {
      skill.setEnabled(0);
    }
    if (skill.getEnabled() == 1) {
      requireEnableQuota(0);
    }
    mapper.insert(skill);
    return mapper.selectById(skill.getId());
  }

  /** 编辑：校验 + name 唯一（builtIn 可编辑） */
  public AgentSkill update(Long id, AgentSkill patch) {
    AgentSkill exists = requireExists(id);
    validate(patch);
    if (!patch.getName().equals(exists.getName())) {
      requireNameFree(patch.getName(), id);
    }
    // 启停走 setEnabled，编辑不改启用状态
    exists.setName(patch.getName());
    exists.setTitle(patch.getTitle());
    exists.setDescription(patch.getDescription());
    exists.setContent(patch.getContent());
    mapper.updateById(exists);
    return mapper.selectById(id);
  }

  /**
   * 删除：内置技能禁删；带包技能删除后 best-effort 清理存储中的包文件
   * （清理失败仅告警不阻断，孤儿文件不影响功能）。
   */
  public void delete(Long id) {
    AgentSkill exists = requireExists(id);
    if (exists.getBuiltIn() != null && exists.getBuiltIn() == 1) {
      throw BizException.badRequest("error.ai.skill.builtInDelete");
    }
    mapper.deleteById(id);
    if (StringUtils.hasText(exists.getPackageFileKey())) {
      try {
        storageService.delete(exists.getPackageFileKey());
      } catch (Exception e) {
        log.warn("技能包存储清理失败 key={}", exists.getPackageFileKey(), e);
      }
    }
  }

  /** 启停：启用前校验数量上限 */
  public void setEnabled(Long id, boolean enabled) {
    AgentSkill exists = requireExists(id);
    if (enabled && (exists.getEnabled() == null || exists.getEnabled() != 1)) {
      requireEnableQuota(1);
    }
    exists.setEnabled(enabled ? 1 : 0);
    mapper.updateById(exists);
  }

  /**
   * 上传技能包：zip 安全校验（解析器三道闸）→ frontmatter 建技能（默认停用）→
   * 原包整体入存储。全部校验通过后才写存储；insert 失败补偿删除存储文件（全有或全无）。
   */
  public AgentSkill uploadPackage(InputStream in, String fileName, long size) {
    if (size <= 0) {
      throw BizException.badRequest("error.ai.skillPkg.fileRequired");
    }
    if (size > MAX_PACKAGE_BYTES) {
      throw BizException.badRequest("error.ai.skillPkg.tooBig", MAX_PACKAGE_BYTES / 1024 / 1024);
    }
    if (fileName == null || !fileName.toLowerCase().endsWith(".zip")) {
      throw BizException.badRequest("error.ai.skillPkg.badExt");
    }
    // 全量读入（≤10MB）：解析与存储需要两次消费同一份字节，保证校验失败时零副作用
    byte[] zipBytes;
    try {
      zipBytes = in.readAllBytes();
    } catch (IOException e) {
      throw BizException.badRequest("error.ai.skillPkg.zipRead");
    }
    if (zipBytes.length > MAX_PACKAGE_BYTES) {
      throw BizException.badRequest("error.ai.skillPkg.tooBig", MAX_PACKAGE_BYTES / 1024 / 1024);
    }
    SkillPackageParser.Parsed parsed = SkillPackageParser.parse(new ByteArrayInputStream(zipBytes));

    AgentSkill skill = new AgentSkill();
    skill.setName(parsed.name());
    // 社区包 frontmatter 无 title 字段（仅 name/description），落库以 name 兜底，管理页可再编辑
    skill.setTitle(StringUtils.hasText(parsed.title()) ? parsed.title() : parsed.name());
    skill.setDescription(parsed.description());
    skill.setContent(parsed.content());
    skill.setEnabled(0);
    skill.setBuiltIn(0);
    validate(skill);
    requireNameFree(skill.getName(), null);

    StoredFile stored;
    try (InputStream storedIn = new ByteArrayInputStream(zipBytes)) {
      stored = storageService.store(storedIn, fileName, zipBytes.length);
    } catch (IOException e) {
      throw BizException.badRequest("error.ai.skillPkg.zipRead");
    }
    skill.setPackageFileKey(stored.key());
    skill.setPackageFileName(fileName);
    skill.setPackageFileSize(stored.size());
    try {
      mapper.insert(skill);
    } catch (RuntimeException e) {
      // 落库失败补偿：回滚存储写入，保持全有或全无
      try {
        storageService.delete(stored.key());
      } catch (Exception cleanupError) {
        log.warn("技能包存储补偿删除失败 key={}", stored.key(), cleanupError);
      }
      throw e;
    }
    return mapper.selectById(skill.getId());
  }

  /** 包内文件清单：路径 + 实际字节数（无包技能 404） */
  public List<SkillPackageParser.PackageEntry> listPackageFiles(Long id) {
    AgentSkill skill = requireExists(id);
    return listPackageFiles(skill);
  }

  /** 包内文件清单（复用给 activate_skill 附注） */
  public List<SkillPackageParser.PackageEntry> listPackageFiles(AgentSkill skill) {
    requirePackage(skill);
    try (InputStream in = storageService.open(skill.getPackageFileKey());
         ZipInputStream zis = new ZipInputStream(in)) {
      List<SkillPackageParser.PackageEntry> entries = new ArrayList<>();
      ZipEntry entry;
      int count = 0;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        // 防御性上限（上传时已校验，存储内容被篡改时不至于拖垮服务）
        if (++count > SkillPackageParser.MAX_ENTRIES) {
          throw BizException.badRequest("error.ai.skillPkg.tooManyEntries",
              SkillPackageParser.MAX_ENTRIES);
        }
        long size = SkillPackageParser.drain(zis, null);
        entries.add(new SkillPackageParser.PackageEntry(
            SkillPackageParser.normalizePath(entry.getName()), size));
      }
      return entries;
    } catch (IOException e) {
      throw BizException.badRequest("error.ai.skillPkg.zipRead");
    }
  }

  /**
   * 读取包内单个文件：路径与清单精确匹配（只读提取，天然免疫路径穿越）；
   * 文本文件按 UTF-8 解码（超 64KB 截断附提示），二进制文件仅返回字节供下载。
   */
  public SkillFileContent readPackageFile(Long id, String path) {
    AgentSkill skill = requireExists(id);
    requirePackage(skill);
    if (!StringUtils.hasText(path)) {
      throw BizException.notFound("error.ai.skillPkg.fileNotFound", String.valueOf(path));
    }
    String target = SkillPackageParser.normalizePath(path);
    if (target.startsWith("/")) {
      target = target.substring(1);
    }
    byte[] bytes = null;
    try (InputStream in = storageService.open(skill.getPackageFileKey());
         ZipInputStream zis = new ZipInputStream(in)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null && bytes == null) {
        if (entry.isDirectory()) {
          continue;
        }
        String entryPath = SkillPackageParser.requireSafePath(entry.getName());
        if (entryPath.equals(target)) {
          ByteArrayOutputStream buf = new ByteArrayOutputStream();
          SkillPackageParser.drain(zis, buf);
          bytes = buf.toByteArray();
        } else {
          SkillPackageParser.drain(zis, null);
        }
      }
    } catch (IOException e) {
      throw BizException.badRequest("error.ai.skillPkg.zipRead");
    }
    if (bytes == null) {
      throw BizException.notFound("error.ai.skillPkg.fileNotFound", target);
    }
    if (SkillPackageParser.isTextFile(target)) {
      String full = new String(bytes, StandardCharsets.UTF_8);
      String content = full.length() > MAX_AI_FILE_CHARS
          ? full.substring(0, MAX_AI_FILE_CHARS) + "\n…（内容超过 64KB 已截断，完整内容请提示管理员在技能管理页下载）"
          : full;
      return new SkillFileContent(target, bytes.length, true, bytes, content);
    }
    return new SkillFileContent(target, bytes.length, false, bytes, null);
  }

  /** 原包下载：流 + 文件名（优先记录的原始 zip 名，缺省用技能名.zip；物理缺失 404） */
  public PackageDownload openPackage(Long id) {
    AgentSkill skill = requireExists(id);
    requirePackage(skill);
    InputStream in;
    try {
      in = storageService.open(skill.getPackageFileKey());
    } catch (Exception e) {
      throw BizException.notFound("error.ai.skillPkg.noPackage");
    }
    String fileName = StringUtils.hasText(skill.getPackageFileName())
        ? skill.getPackageFileName() : skill.getName() + ".zip";
    return new PackageDownload(in, fileName);
  }

  /** 原包下载对象：stream 由调用方关闭 */
  public record PackageDownload(InputStream stream, String fileName) {
  }

  /** 单文件读取结果：text=true 时 textContent 供 AI 工具加载；bytes 供下载 */
  public record SkillFileContent(String path, long size, boolean text, byte[] bytes,
                                 String textContent) {
  }

  // ------------------------------------------------------------------

  private AgentSkill requireExists(Long id) {
    AgentSkill skill = mapper.selectById(id);
    if (skill == null) {
      throw BizException.badRequest("error.ai.skill.notFound");
    }
    return skill;
  }

  /** 带包校验：无包技能（含内置种子）的文件浏览/下载请求一律 404 */
  private void requirePackage(AgentSkill skill) {
    if (!StringUtils.hasText(skill.getPackageFileKey())) {
      throw BizException.notFound("error.ai.skillPkg.noPackage");
    }
  }

  private void requireEnableQuota(int currentEnabledCount) {
    long enabled = mapper.selectCount(new QueryWrapper<AgentSkill>().eq("enabled", 1));
    if (enabled + currentEnabledCount > MAX_ENABLED_SKILLS) {
      throw BizException.badRequest("error.ai.skill.enabledLimit", MAX_ENABLED_SKILLS);
    }
  }

  private void requireNameFree(String name, Long excludeId) {
    QueryWrapper<AgentSkill> q = new QueryWrapper<AgentSkill>().eq("name", name);
    if (excludeId != null) {
      q.ne("id", excludeId);
    }
    if (mapper.selectCount(q) > 0) {
      throw BizException.badRequest("error.ai.skill.nameDup", name);
    }
  }

  private void validate(AgentSkill skill) {
    if (!StringUtils.hasText(skill.getName()) || !StringUtils.hasText(skill.getTitle())
        || !StringUtils.hasText(skill.getDescription()) || !StringUtils.hasText(skill.getContent())) {
      throw BizException.badRequest("error.ai.skill.required");
    }
    if (!skill.getName().matches(NAME_PATTERN)) {
      throw BizException.badRequest("error.ai.skill.nameFormat");
    }
    if (skill.getDescription().length() > MAX_DESCRIPTION_CHARS) {
      throw BizException.badRequest("error.ai.skill.descTooLong", MAX_DESCRIPTION_CHARS);
    }
    if (skill.getContent().length() > MAX_CONTENT_CHARS) {
      throw BizException.badRequest("error.ai.skill.contentTooLong", MAX_CONTENT_CHARS);
    }
    if (skill.getTitle().length() > 64) {
      throw BizException.badRequest("error.ai.skill.tooLongTitle");
    }
  }
}
