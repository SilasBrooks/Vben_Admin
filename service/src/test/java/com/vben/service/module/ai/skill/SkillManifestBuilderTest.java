package com.vben.service.module.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vben.service.module.ai.skill.mapper.AgentSkillMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** 技能清单构建器单测：无技能零注入、清单内容（渐进式披露第一级）与超长截断 */
class SkillManifestBuilderTest {

  private final AgentSkillMapper mapper = Mockito.mock(AgentSkillMapper.class);
  private final SkillManifestBuilder builder = new SkillManifestBuilder(mapper);

  private AgentSkill skill(String name, String description) {
    AgentSkill s = new AgentSkill();
    s.setName(name);
    s.setTitle("标题-" + name);
    s.setDescription(description);
    return s;
  }

  @Test
  void noSkillsYieldsEmptyManifest() {
    when(mapper.selectList(any())).thenReturn(List.of());
    assertThat(builder.build()).isEmpty();
  }

  @Test
  void manifestListsEnabledSkillsWithActivationHint() {
    when(mapper.selectList(any())).thenReturn(List.of(
        skill("announcement-writer", "撰写站内公告"),
        skill("meeting-minutes", "整理会议纪要")));

    String manifest = builder.build();

    assertThat(manifest)
        .contains("## 可用技能")
        .contains("activate_skill")
        .contains("load_skill_file")
        .contains("announcement-writer 标题-announcement-writer：撰写站内公告")
        .contains("meeting-minutes 标题-meeting-minutes：整理会议纪要");
  }

  @Test
  void manifestTruncatesBeyondLimit() {
    AgentSkill big = skill("big-skill", "介".repeat(3800));
    // 追加第二个技能必然突破上限 → 截断并注记
    AgentSkill second = skill("second-skill", "乙".repeat(100));
    when(mapper.selectList(any())).thenReturn(List.of(big, second));

    String manifest = builder.build();

    // 第二个技能放不下：截断并注记，绝不静默丢弃语义
    assertThat(manifest).contains("big-skill").doesNotContain("second-skill");
    assertThat(manifest).endsWith("（更多技能已省略）");
  }
}
