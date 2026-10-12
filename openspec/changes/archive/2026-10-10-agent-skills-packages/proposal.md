## Why

当前 AI 技能只支持管理页在线维护「SKILL.md 正文」等价物（单条 DB 记录），无法承载 Claude 官方 Agent Skills 的目录包形态（`skill-name/SKILL.md` + `references/` + `assets/` + `scripts/`）。社区已沉淀大量可直接下载使用的技能包（zip），管理员只能手工拆开复制粘贴，references 参考文档无处安放，能力包的可迁移性与生态兼容性受限。

## What Changes

- 新增技能包上传：管理页上传 `skill-name.zip`（≤10MB），后端解析包内 `SKILL.md` 的 YAML frontmatter（name/description）与正文，自动创建技能记录（默认停用），原 zip 存入文件存储（复用 `StorageService` 可插拔存储）
- 包文件浏览与下载：新增接口列出包内文件清单（路径/大小/文本二进制标记），支持单个文件下载与原包整体下载；`agent_skill` 新增 `package_file_key` / `package_file_name` / `package_file_size` 三列
- 渐进式披露第三级：AI 对话新增 `load_skill_file` 查询工具（name + path 加载包内文本文件，二进制文件提示管理页下载）；`activate_skill` 返回正文时附带包内文件清单提示，引导模型按需加载
- 安全校验：zip 条目数与解压总大小上限、拒绝路径穿越条目（`../` / 绝对路径）、SKILL.md 必须存在于包根或唯一一级目录；scripts 类文件仅展示与查看，MUST NOT 被 Agent 执行
- 前端：技能页工具栏新增「上传技能包」（System:Skill:Edit 权限，幂等 + 限流），行操作新增「文件」弹窗（文件清单 + 下载）；不新增权限码（上传复用 System:Skill:Edit，文件浏览/下载复用 System:Skill:List）
- 边界（Non-Goal）：scripts 不执行；内置种子技能无包文件、行为不变；删除技能时同步清理存储中的包文件（best effort）

## Capabilities

### New Capabilities

（无——本变更是既有 `ai/agent-skills` 能力的能力增强，不引入新能力域）

### Modified Capabilities

- `ai/agent-skills`：技能数据模型扩展包文件字段；管理接口新增包上传/文件清单/文件下载/原包下载；新增 `load_skill_file` AI 工具与 `activate_skill` 文件清单附注；前端技能页新增上传入口与文件管理弹窗

## Impact

- 后端：`module/ai/skill/`（实体字段、Service 上传解析与文件读取、Controller 新端点）、`module/ai/tool/AiSkillTools`（新工具）、`common/storage`（复用不改）、`db/`（存量库 upgrade SQL：agent_skill 加三列，幂等）
- 前端：`views/system/skill/`（上传按钮 + 文件弹窗）、`api/system/skill.ts`、locales 中英语言包
- 兼容性：纯增量——既有手工技能（含内置技能）无包文件，全部既有接口与页面行为不变
- 安全面：zip 解析（zip bomb / 路径穿越 / 条目上限）、文件读取按包内路径精确匹配（只读提取）；上传与下载走既有审计/限流约定
