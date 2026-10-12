package com.vben.service.module.ai.skill;

import com.vben.service.common.OperLog;
import com.vben.service.common.R;
import com.vben.service.common.idempotent.Idempotent;
import com.vben.service.common.ratelimit.RateLimit;
import com.vben.service.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 技能管理接口（系统管理-AI 技能）。
 *
 * <p>权限码对齐前端 v-access：
 * <ul>
 *   <li>System:Skill:List    列表 / 包文件浏览与下载</li>
 *   <li>System:Skill:Add     新增</li>
 *   <li>System:Skill:Edit    编辑/启停/上传技能包</li>
 *   <li>System:Skill:Delete  删除</li>
 * </ul>
 */
@RestController
@Tag(name = "AI 技能", description = "Agent 技能能力包管理（渐进式披露扩展 AI 助手能力）")
@RequestMapping("/system/skill")
@RequiredArgsConstructor
public class SkillController {

  private final AgentSkillService skillService;

  /** 分页列表：名称/标题模糊过滤 */
  @Operation(summary = "技能列表", description = "名称/标题模糊过滤 + 分页")
  @RequirePermission("System:Skill:List")
  @GetMapping("/list")
  public R<Map<String, Object>> list(
      @RequestParam(defaultValue = "1") long pageNo,
      @RequestParam(defaultValue = "10") long pageSize,
      @RequestParam(required = false) String keyword) {
    var page = skillService.page(pageNo, pageSize, keyword);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("items", page.getRecords());
    data.put("total", page.getTotal());
    return R.ok(data);
  }

  /** 新增技能 */
  @Operation(summary = "新增技能", description = "name 唯一（小写字母/数字/连字符）；正文 ≤8000 字符")
  @RequirePermission("System:Skill:Add")
  @RateLimit(name = "skill:save", limit = 20, windowSeconds = 60)
  @Idempotent(name = "skill:add", intervalSeconds = 3)
  @PostMapping
  public R<AgentSkill> add(@RequestBody AgentSkill skill) {
    return R.ok(skillService.create(skill));
  }

  /** 编辑技能（内置技能可编辑） */
  @Operation(summary = "编辑技能", description = "name 唯一；启停状态由独立接口控制，编辑不影响启停")
  @RequirePermission("System:Skill:Edit")
  @RateLimit(name = "skill:save", limit = 20, windowSeconds = 60)
  @PutMapping("/{id}")
  public R<AgentSkill> update(@PathVariable Long id, @RequestBody AgentSkill skill) {
    return R.ok(skillService.update(id, skill));
  }

  /** 删除技能（内置技能禁删） */
  @Operation(summary = "删除技能", description = "内置种子技能不可删除，可停用")
  @RequirePermission("System:Skill:Delete")
  @OperLog(module = "AI 技能", description = "删除AI技能")
  @DeleteMapping("/{id}")
  public R<Void> delete(@PathVariable Long id) {
    skillService.delete(id);
    return R.ok();
  }

  /** 启停技能（启用有数量上限；修改下一轮对话即生效） */
  @Operation(summary = "启停技能", description = "启用数量上限 20；启停下一次对话即生效无需重启")
  @RequirePermission("System:Skill:Edit")
  @OperLog(module = "AI 技能", description = "启停AI技能")
  @Idempotent(name = "skill:toggle", intervalSeconds = 2)
  @PutMapping("/{id}/enabled")
  public R<Void> setEnabled(@PathVariable Long id, @RequestBody EnabledReq req) {
    skillService.setEnabled(id, Boolean.TRUE.equals(req.getEnabled()));
    return R.ok();
  }

  /** 启停请求体 */
  @Data
  public static class EnabledReq {
    /** true=启用 false=停用 */
    private Boolean enabled;
  }

  // ------------------------------------------------------------------
  // 技能包（Claude 目录包 zip）：上传解析 / 文件清单 / 文件下载 / 原包下载
  // ------------------------------------------------------------------

  /** 上传技能包：解析包内 SKILL.md frontmatter 建技能（默认停用），原包入存储 */
  @Operation(summary = "上传技能包",
      description = "zip ≤10MB（条目 ≤200、解压 ≤20MB，拒绝路径穿越）；"
          + "SKILL.md 须位于包根或唯一一级目录，frontmatter 提供 name/description；"
          + "创建的技能默认停用")
  @RequirePermission("System:Skill:Edit")
  @RateLimit(name = "skill:package", limit = 10, windowSeconds = 60)
  @Idempotent(name = "skill:package", intervalSeconds = 3)
  @OperLog(module = "AI 技能", description = "上传技能包")
  @PostMapping("/package")
  public R<AgentSkill> uploadPackage(@RequestParam("file") MultipartFile file) throws Exception {
    if (file == null || file.isEmpty()) {
      throw com.vben.service.common.BizException.badRequest("error.ai.skillPkg.fileRequired");
    }
    try (InputStream in = file.getInputStream()) {
      return R.ok(skillService.uploadPackage(in, file.getOriginalFilename(), file.getSize()));
    }
  }

  /** 包内文件清单：路径 + 实际字节数（无包技能 404） */
  @Operation(summary = "技能包文件清单", description = "返回包内全部条目的相对路径与字节数")
  @RequirePermission("System:Skill:List")
  @GetMapping("/{id}/files")
  public R<Map<String, Object>> files(@PathVariable Long id) {
    var entries = skillService.listPackageFiles(id);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("items", entries);
    data.put("total", entries.size());
    return R.ok(data);
  }

  /**
   * 单文件下载：按包内路径精确匹配提取（路径不存在 404）；
   * 文件名取路径末段，Content-Disposition 走 RFC 5987 避免中文乱码。
   */
  @Operation(summary = "下载包内文件", description = "按包内路径精确匹配提取附件下载")
  @RequirePermission("System:Skill:List")
  @GetMapping("/{id}/files/content")
  public void fileContent(@PathVariable Long id, @RequestParam String path,
                          HttpServletResponse response) throws Exception {
    AgentSkillService.SkillFileContent content = skillService.readPackageFile(id, path);
    response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    String fileName = content.path().contains("/")
        ? content.path().substring(content.path().lastIndexOf('/') + 1)
        : content.path();
    response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
        + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
    response.setContentLength(content.bytes().length);
    response.getOutputStream().write(content.bytes());
  }

  /** 原包下载：返回上传时的原始 zip */
  @Operation(summary = "下载技能原包", description = "返回上传时的原始 zip 文件")
  @RequirePermission("System:Skill:List")
  @GetMapping("/{id}/package")
  public void packageDownload(@PathVariable Long id, HttpServletResponse response) throws Exception {
    var download = skillService.openPackage(id);
    response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
        + URLEncoder.encode(download.fileName(), StandardCharsets.UTF_8));
    try (InputStream in = download.stream()) {
      in.transferTo(response.getOutputStream());
    }
  }
}
