## Context

AI 助手骨架（`AiChatService` + `AiToolRegistry`）已稳定：系统提示词是 `SYSTEM_PROMPT` 常量，每轮对话首条注入；工具经 `@AiAgentTool` 自动注册、按权限动态下发。技能机制要动的只有两处：系统提示词从「常量」变「常量 + 清单拼接」，以及新增一个查询工具。与 kb-rag-mvp 完全解耦（纯新表、无 pgvector），可独立实施独立归档。

## Goals / Non-Goals

**Goals:**
- 渐进式披露两级：清单常驻（name+description，低 token）→ activate_skill 按需加载正文（MVP 两级即可覆盖 Claude Skills 核心价值）
- 管理即生效：启停/编辑/新增零重启
- 内置示范技能让概念开箱可演示
- 防膨胀：长度/数量上限，无技能时零侵入

**Non-Goals:**
- 不做 zip/SKILL.md 文件上传（表单直填正文；Claude Code 的目录式技能含 scripts/assets，管理后台场景用不上）
- 不做按角色技能可见性（登录即可用全部启用技能）
- 不做版本历史/审计 diff（操作日志走既有 @OperLog 即可）

## Decisions

### D1：两级渐进披露，不做三级
Claude 完整机制是三级（元数据 → SKILL.md 正文 → 正文引用的资源文件）。管理后台场景的技能是纯指令文本（无附属资源），两级足够：常驻清单让模型「知道有什么」，activate_skill 返回完整正文让模型「知道怎么做」。资源文件级留待真实需求出现再加。

### D2：系统提示词动态组装，MVP 每轮查库
`AiChatService.chat` 开头把 `SYSTEM_PROMPT` 与技能清单段拼接后作为首条 system 消息。启用技能 ≤20、每条 description ≤300 字符，单次查询轻量，不做缓存；后续有性能诉求可加短 TTL 缓存（决策留扩展点，不预先实现）。

清单段格式（伪）：
```
## 可用技能
以下技能可提供专业方法论，需要时先调用 activate_skill(name) 加载完整指引：
- announcement-writer 公告写作助手：按站内公告规范起草公告正文…
（无启用技能时整段省略）
```

### D3：activate_skill 作为普通 QUERY 工具
新建 `AiSkillTools`（@Component）声明 `activate_skill`（AiToolKind.QUERY、无权限码），走既有自动注册与自动执行回喂链路，零编排层改动。失败（不存在/停用）返回状态说明字符串而非异常——与 search_knowledge 同一约定，模型可转述。

### D4：表结构与内置技能
`agent_skill`：id、name（唯一索引）、title、description、content（TEXT）、enabled、built_in、create_time、update_time（MetaObjectHandler 填充，不用触发器）。种子 2 技能：`announcement-writer`（引用站内公告的字段规范与受众习惯，产出后引导走 publish_announcement 工具发布）、`connection-troubleshooter`（本仓库 AGENTS.md 第 2 节排查链路的方法论化：Docker Desktop → 容器状态 → 端口 → 编译问题）。

### D5：上限即防线
技能正文是管理员维护的可信内容，但仍按不可信输入设防：content ≤8000 字符、description ≤300 字符、启用 ≤20 条、清单段总长上限（如 4000 字符，超限截断尾部并注记）。name 正则 `^[a-z0-9][a-z0-9-]{1,63}$` 防奇异字符进入提示词。

### D6：模块归属与菜单
后端 `module/ai/skill/`（controller/service/entity/mapper 子包）。菜单挂系统管理 leaf order 11「AI 技能」（`SystemSkill`，`/system/skill`，super,admin），权限码 `System:Skill:List/Add/Edit/Delete`。`AiSystemTools.MENU_TITLE_ZH` 同步。

## Risks / Trade-offs

- [技能正文构成 prompt 注入面] 管理员可信 + 长度上限 + name 正则白名单；工具/数据边界不变（技能只给指令，不给执行权）→ 可接受
- [每轮查库组装清单] 量级极小（≤20 行短文本）；先简单后优化
- [模型不主动加载技能] 靠清单调用指引 + description 写清触发场景；内置技能 E2E 验证加载行为，不达标则调 description 措辞
- [正文超长挤占上下文] 8000 字符上限 ≈ 数千 token，单技能加载可控；多技能叠加场景靠「按需只加载一个」语义约束

## Migration Plan

1. schema 初始化器 + upgrade SQL 双通道建 `agent_skill` 表与种子数据（存量库手动执行）
2. 上线即生效，无数据迁移；回滚停用技能即可（表保留无害）
