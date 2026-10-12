package com.vben.service.module.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vben.service.common.BizException;
import com.vben.service.module.ai.skill.AgentSkill;
import com.vben.service.module.ai.skill.AgentSkillService;
import com.vben.service.module.ai.skill.SkillPackageParser.PackageEntry;
import com.vben.service.module.ai.skill.mapper.AgentSkillMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

/**
 * 技能 AI 工具单测：activate_skill（第二级：正文 + 包内文件清单附注）与
 * load_skill_file（第三级：文本加载/二进制指引/停用如实告知）。
 * 失败均返回状态说明而非抛异常，保证对话不中断。
 */
class AiSkillToolsTest {

  private final AgentSkillMapper skillMapper = Mockito.mock(AgentSkillMapper.class);
  private final AgentSkillService skillService = Mockito.mock(AgentSkillService.class);
  private final AiSkillTools tools = new AiSkillTools(skillMapper, skillService);

  private AgentSkill skill(String name, boolean withPackage) {
    AgentSkill skill = new AgentSkill();
    skill.setId(1L);
    skill.setName(name);
    skill.setContent("# 写作指引\n先列大纲。");
    if (withPackage) {
      skill.setPackageFileKey("skill/pkg.zip");
    }
    return skill;
  }

  // ------------------------------------------------------------------
  // activate_skill（第二级）
  // ------------------------------------------------------------------

  @Test
  void blankNameReturnsHintInsteadOfThrowing() {
    AiToolResult result = tools.activateSkill("  ");
    assertThat(result.ok()).isTrue();
    assertThat(result.content()).contains("不能为空");
  }

  @Test
  void unknownOrDisabledSkillReturnsHonestStatus() {
    when(skillMapper.selectOne(any())).thenReturn(null);

    AiToolResult result = tools.activateSkill("no-such");

    assertThat(result.ok()).isTrue();
    assertThat(result.content()).contains("技能不存在或已停用").contains("no-such");
  }

  @Test
  void foundSkillReturnsFullContent() {
    when(skillMapper.selectOne(any())).thenReturn(skill("writer", false));

    AiToolResult result = tools.activateSkill("writer");

    assertThat(result.ok()).isTrue();
    assertThat(result.content()).isEqualTo("# 写作指引\n先列大纲。");
  }

  @Test
  void foundSkillWithPackageAppendsFileNote() {
    AgentSkill packaged = skill("code-reviewer", true);
    when(skillMapper.selectOne(any())).thenReturn(packaged);
    when(skillService.listPackageFiles(packaged)).thenReturn(List.of(
        new PackageEntry("code-reviewer/references/spec.md", 12),
        new PackageEntry("code-reviewer/assets/logo.png", 3)));

    AiToolResult result = tools.activateSkill("code-reviewer");

    // 正文 + 文件清单附注（引导第三级披露），附注里给出 load_skill_file 用法
    assertThat(result.content()).startsWith("# 写作指引");
    assertThat(result.content()).contains("load_skill_file")
        .contains("code-reviewer/references/spec.md");
  }

  @Test
  void packageListingFailureDoesNotBreakContent() {
    AgentSkill packaged = skill("code-reviewer", true);
    when(skillMapper.selectOne(any())).thenReturn(packaged);
    when(skillService.listPackageFiles(packaged))
        .thenThrow(BizException.badRequest("error.ai.skillPkg.zipRead"));

    AiToolResult result = tools.activateSkill("code-reviewer");

    // 清单读取失败降级为纯正文，不让第二级披露整体失败
    assertThat(result.ok()).isTrue();
    assertThat(result.content()).isEqualTo("# 写作指引\n先列大纲。");
  }

  // ------------------------------------------------------------------
  // load_skill_file（第三级）
  // ------------------------------------------------------------------

  @Test
  void loadSkillFileBlankParamsReturnHint() {
    assertThat(tools.loadSkillFile(" ", "references/spec.md").content()).contains("不能为空");
    assertThat(tools.loadSkillFile("writer", " ").content()).contains("不能为空");
  }

  @Test
  void loadSkillFileRejectsUnknownOrDisabledSkill() {
    when(skillMapper.selectOne(any())).thenReturn(null);

    AiToolResult result = tools.loadSkillFile("off-skill", "references/spec.md");

    assertThat(result.ok()).isTrue();
    assertThat(result.content()).contains("技能不存在或已停用").contains("off-skill");
  }

  @Test
  void loadSkillFileReturnsTextContent() {
    when(skillMapper.selectOne(any())).thenReturn(skill("code-reviewer", true));
    when(skillService.readPackageFile(1L, "references/spec.md"))
        .thenReturn(new AgentSkillService.SkillFileContent(
            "references/spec.md", 12, true, new byte[12], "# 规格\n先看接口。"));

    AiToolResult result = tools.loadSkillFile("code-reviewer", "references/spec.md");

    assertThat(result.ok()).isTrue();
    assertThat(result.content()).isEqualTo("# 规格\n先看接口。");
  }

  @Test
  void loadSkillFileBinaryGivesDownloadHint() {
    when(skillMapper.selectOne(any())).thenReturn(skill("code-reviewer", true));
    when(skillService.readPackageFile(1L, "assets/logo.png"))
        .thenReturn(new AgentSkillService.SkillFileContent(
            "assets/logo.png", 3, false, new byte[3], null));

    AiToolResult result = tools.loadSkillFile("code-reviewer", "assets/logo.png");

    assertThat(result.ok()).isTrue();
    assertThat(result.content()).contains("二进制").contains("下载");
  }

  @Test
  void loadSkillFileForwardsBusinessErrorsHonestly() {
    when(skillMapper.selectOne(any())).thenReturn(skill("code-reviewer", true));
    // 直接构造带文案的异常：单测无请求上下文，I18nMessage 按设计回退为 key
    when(skillService.readPackageFile(1L, "no/such.md"))
        .thenThrow(new BizException(404, "技能包内不存在文件 no/such.md"));

    AiToolResult result = tools.loadSkillFile("code-reviewer", "no/such.md");

    assertThat(result.ok()).isTrue();
    assertThat(result.content()).contains("no/such.md");
  }
}
