package com.vben.service.module.ai.skill;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 技能（Claude 式能力包）：指导模型「怎么做事」的方法论数据。
 *
 * <p>渐进式披露两级：启用技能的 name+description 清单常驻系统提示词；
 * 模型按需经 activate_skill 工具加载完整正文。管理员增删改即生效，无需重启。
 */
@Data
@TableName("agent_skill")
public class AgentSkill {

  @TableId(type = IdType.AUTO)
  private Long id;

  /** 技能标识（唯一，小写字母/数字/连字符），activate_skill 的参数 */
  private String name;

  /** 中文能力名 */
  private String title;

  /** 场景描述（给模型看：什么需求该用本技能），≤300 字符 */
  private String description;

  /** 技能正文（Markdown 指令），≤8000 字符 */
  private String content;

  /** 1 启用 0 停用（仅启用技能进入清单） */
  private Integer enabled;

  /** 1=内置种子技能（可编辑可停用，不可删除） */
  private Integer builtIn;

  /** 技能包 zip 存储键（StorageService key），NULL=无包的手工技能 */
  private String packageFileKey;

  /** 技能包原始 zip 文件名（下载展示用） */
  private String packageFileName;

  /** 技能包字节数 */
  private Long packageFileSize;

  @TableField(fill = FieldFill.INSERT)
  private LocalDateTime createTime;

  @TableField(fill = FieldFill.INSERT_UPDATE)
  private LocalDateTime updateTime;
}
