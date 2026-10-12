package com.vben.service.module.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vben.service.common.BizException;
import com.vben.service.module.ai.skill.AgentSkill;
import com.vben.service.module.ai.skill.AgentSkillService;
import com.vben.service.module.ai.skill.mapper.AgentSkillMapper;
import com.vben.service.module.ai.tool.annotation.AiAgentTool;
import com.vben.service.module.ai.tool.annotation.AiToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

/**
 * Agent 技能 AI 工具：activate_skill（第二级）+ load_skill_file（第三级）。
 *
 * <p>无权限码（登录即可调用，启用技能面向全体用户）；返回状态说明而非异常，
 * 模型可如实转述、对话不中断。仅启用技能可见可加载，停用技能对模型不可见。
 *
 * <p>三级渐进式披露：系统提示词清单 → activate_skill 正文（附包内文件清单）→
 * load_skill_file 按需加载参考文件（文本 ≤64KB 截断；二进制给下载指引）。
 */
@Component
@RequiredArgsConstructor
public class AiSkillTools {

  private final AgentSkillMapper skillMapper;
  private final AgentSkillService skillService;

  @AiAgentTool(name = "activate_skill", title = "加载技能指引", kind = AiToolKind.QUERY,
      description = "按技能标识（name）加载技能的完整方法论指引（Markdown），返回后严格按指引执行任务。"
          + "仅当系统提示词「可用技能」清单中的技能与当前任务匹配时调用；"
          + "一次只加载一个技能，任务不匹配任何技能时不要调用。")
  public AiToolResult activateSkill(
      @AiToolParam(value = "技能标识（清单中的 name，如 announcement-writer）", required = true)
      String skillName) {
    if (!StringUtils.hasText(skillName)) {
      return AiToolResult.success("技能标识不能为空，请传入「可用技能」清单中的 name");
    }
    AgentSkill skill = skillMapper.selectOne(new QueryWrapper<AgentSkill>()
        .eq("name", skillName.trim())
        .eq("enabled", 1)
        .last("LIMIT 1"));
    if (skill == null) {
      return AiToolResult.success("技能不存在或已停用：" + skillName.trim()
          + "。请如实告知用户，不要编造指引内容");
    }
    return AiToolResult.success(skill.getContent() + packageFileNote(skill));
  }

  @AiAgentTool(name = "load_skill_file", title = "加载技能参考文件", kind = AiToolKind.QUERY,
      description = "按技能标识（name）与包内路径（path）加载技能包中的文本参考文件，"
          + "如 references/ 下的规格与示例文档（第三级披露）。"
          + "path 只能取 activate_skill 返回的文件清单中的路径；二进制文件不可加载。")
  public AiToolResult loadSkillFile(
      @AiToolParam(value = "技能标识（清单中的 name）", required = true) String skillName,
      @AiToolParam(value = "包内文件路径（来自 activate_skill 返回的清单，如 references/spec.md）",
          required = true) String path) {
    if (!StringUtils.hasText(skillName) || !StringUtils.hasText(path)) {
      return AiToolResult.success("技能标识与包内路径均不能为空。path 只能取 "
          + "activate_skill 返回的文件清单中的路径");
    }
    AgentSkill skill = skillMapper.selectOne(new QueryWrapper<AgentSkill>()
        .eq("name", skillName.trim())
        .eq("enabled", 1)
        .last("LIMIT 1"));
    if (skill == null) {
      return AiToolResult.success("技能不存在或已停用：" + skillName.trim()
          + "。请如实告知用户，不要编造文件内容");
    }
    try {
      AgentSkillService.SkillFileContent file = skillService.readPackageFile(skill.getId(), path.trim());
      if (file.text()) {
        return AiToolResult.success(file.textContent());
      }
      return AiToolResult.success("文件 " + file.path()
          + " 是二进制文件，无法直接加载。请提示管理员在「系统管理 → AI 技能」该技能的"
          + "「文件」弹窗中下载查看");
    } catch (BizException e) {
      // 404（无包/路径不存在）等业务错误如实转述，保证对话不中断
      return AiToolResult.success(e.getMessage());
    }
  }

  /** 带包技能在正文后附文件清单附注（无包不附；清单读取失败不阻断正文返回） */
  private String packageFileNote(AgentSkill skill) {
    if (!StringUtils.hasText(skill.getPackageFileKey())) {
      return "";
    }
    try {
      var files = skillService.listPackageFiles(skill);
      if (files.isEmpty()) {
        return "";
      }
      String paths = files.stream()
          .map(f -> f.path())
          .collect(Collectors.joining("、"));
      return "\n\n---\n本技能附带参考文件：" + paths
          + "\n可用 load_skill_file 工具（name=技能标识，path=文件路径）按需加载阅读。";
    } catch (BizException e) {
      return "";
    }
  }
}
