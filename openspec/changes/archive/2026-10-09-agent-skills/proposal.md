## Why

AI 助手的能力扩展目前只有一条路：改代码加 `@AiAgentTool` 工具。「按公司规范起草公告」「按固定套路写周报」「连接失败怎么排查」这类方法论型能力不适合做成系统数据工具——它们是指导模型怎么做事的「操作手册」，不是查库写库的动作。参照 Claude 的 Agent Skills 机制（渐进式披露：清单常驻、正文按需加载），把这类能力做成管理员可在界面上增删改的 SKILL.md 能力包，AI 助手运行时动态吸收新能力，全程不改代码。

## What Changes

- 新增 `agent_skill` 技能表：name（唯一、小写连字符）、给模型看的场景 description、SKILL.md 正文、启停状态、内置标记
- 渐进式披露两级加载：
  1. **常驻清单**——每轮对话把启用技能的「name + description」注入系统提示词（低 token 占用，让模型知道有什么能力）
  2. **按需加载**——新增 `activate_skill` 查询工具，模型判定需要时调用，返回该技能完整正文参与本轮作答
- DatabaseSeeder 内置 2 个示范技能（公告写作助手、故障排查手册），开箱可演示概念
- 新增管理页面「系统管理 → AI 技能」（order 11）：列表 + 新增/编辑/删除/启停，正文编辑用多行文本
- 安全约束：正文长度上限、启用技能数量上限、name 格式校验，防 prompt 注入膨胀

**Non-Goals（本期不做）**：技能文件上传/zip 解包、多文件脚本资源（Claude Code 的 scripts/assets 形态）、按角色隔离技能可见性、技能版本历史、前端手动调试加载。

## Capabilities

### New Capabilities

- `ai/agent-skills`: 技能能力包管理（CRUD、启停、内置种子、格式与长度约束）与常驻清单/按需加载的完整语义

### Modified Capabilities

- `ai/assistant`: 新增「技能清单注入与按需加载」Requirement（ADDED）——系统提示词动态拼接技能清单、`activate_skill` 查询工具语义、加载失败不阻断对话

## Impact

- **后端**：新建 `module/ai/skill/`（entity/mapper/service/controller）；`AiChatService` 系统提示词从常量改为「常量 + 启用技能清单」动态组装（每轮对话开始查一次启用技能）；`module/ai/tool/` 新增 `AiSkillTools`（activate_skill）；`schema-postgres.sql` 加表；`DatabaseSeeder` 加菜单/权限码/种子技能；存量库 upgrade SQL；`AiSystemTools.MENU_TITLE_ZH` 同步；`messages*.properties` 双语错误文案
- **前端**：`views/system/skill/` 管理页；locales 中英同构；api 模块新增
- **权限码**：`System:Skill:List/Add/Edit/Delete`（F 型挂菜单下）
- **无数据库扩展依赖**：纯新表，不涉及 pgvector，先于或后于 kb-rag-mvp 实施均可独立归档
