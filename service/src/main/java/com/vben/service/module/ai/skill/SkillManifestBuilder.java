package com.vben.service.module.ai.skill;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vben.service.module.ai.skill.mapper.AgentSkillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 技能清单构建器：把启用技能拼为系统提示词尾部的「可用技能」段（渐进式披露第一级）。
 *
 * <p>清单只含 name/title/description（低 token 占用），完整正文由模型经
 * activate_skill 按需加载。无启用技能返回空串（零注入，对话行为不变）；
 * 总长超 {@link #MAX_MANIFEST_CHARS} 截断尾部并注记，防上下文爆炸。
 */
@Component
@RequiredArgsConstructor
public class SkillManifestBuilder {

  /** 清单段总长上限（字符） */
  public static final int MAX_MANIFEST_CHARS = 4000;

  private final AgentSkillMapper mapper;

  /** 构建清单段；无启用技能返回空串 */
  public String build() {
    List<AgentSkill> skills = mapper.selectList(new QueryWrapper<AgentSkill>()
        .eq("enabled", 1)
        .orderByAsc("name"));
    if (skills.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder("""
        ## 可用技能
        以下技能提供专业方法论。当用户任务与某技能的场景匹配时，先调用 activate_skill
        工具（参数 name=技能标识）加载完整指引；指引末尾附带的包内参考文件（如有）可用
        load_skill_file 工具（参数 name=技能标识、path=文件路径）按需深入阅读；
        然后严格按指引执行。任务不匹配任何技能时忽略本节：
        """);
    for (AgentSkill skill : skills) {
      String line = "\n- " + skill.getName() + " " + skill.getTitle()
          + "：" + skill.getDescription();
      if (sb.length() + line.length() > MAX_MANIFEST_CHARS) {
        sb.append("\n（更多技能已省略）");
        break;
      }
      sb.append(line);
    }
    return sb.toString();
  }
}
