## 1. 数据库

- [x] 1.1 `schema-postgres.sql` 增加 `agent_skill` 表（name 唯一索引、content TEXT、enabled/built_in）；存量 vben5 库经 upgrade SQL 建表成功
- [x] 1.2 新增 `db/upgrade-20261009-agent-skills-postgres.sql`（建表 + 菜单/权限码 INSERT + 内置技能种子，与 DatabaseSeeder 一致）；对存量 vben5 库手动执行后 AI 技能菜单与 3 条种子可见

## 2. 后端

- [x] 2.1 `module/ai/skill/`：entity + mapper 子包 + `AgentSkillService`（CRUD/启停/name 正则与唯一/description≤300/content≤8000/启用≤20/内置禁删），错误码 `error.ai.skill.*` 中英双语；单测 11 用例覆盖上限与内置禁删
- [x] 2.2 SkillController（`/system/skill`，@RequirePermission + @Idempotent/@RateLimit/@OperLog 按约定）；E2E 浏览器增改启停全链路验证
- [x] 2.3 `AiChatService` 系统提示词动态组装：常量 + 启用技能清单段（含调用指引、总长上限截断、无技能零注入）；单测 2 用例断言有/无技能两种 system 消息
- [x] 2.4 `AiSkillTools.activate_skill`（@AiAgentTool QUERY、无权限码；存在且启用返回正文，否则状态说明）；单测 3 分支；E2E 日志证据 `WHERE (name = ? AND enabled = ?)` 调用生效
- [x] 2.5 `DatabaseSeeder` 加菜单（系统管理 leaf「AI 技能」）、权限码 `System:Skill:*`、内置技能；`AiSystemTools.MENU_TITLE_ZH` 同步
- [x] 2.6 `mvn test` 全绿（24 类 166 用例）

## 3. 前端

- [x] 3.1 `api/system/skill.ts` 对接 REST（与 llm.ts 同目录惯例）；`pnpm check:type` 通过
- [x] 3.2 `views/system/skill/` 管理页（useVbenVxeGrid 列表 + useVbenModal 表单：name/title/description、content 多行 + 字数提示、启停、内置技能删除禁用，v-access:code 按钮权限）；浏览器新增/启停验证，内置删除按钮 disabled
- [x] 3.3 locales 中英同构 `page.system.skill` 与按钮文案；`pnpm check:type` 通过

## 4. 验证与收尾

- [x] 4.1 E2E：浏览器登录 → 新建技能（默认停用）→ 启用后 AI 对话触发 activate_skill（后端日志 SQL 证据）→ 回答遵循技能规范；停用零影响场景由 AiChatServiceTest 无技能零注入单测覆盖
- [x] 4.2 根 README「项目亮点/功能清单/注意事项」同步（Agent Skills 渐进式披露关键词）
- [x] 4.3 归档 openspec 变更（sync delta → archive）：`openspec validate agent-skills --strict` 通过后 `openspec archive agent-skills --yes`
