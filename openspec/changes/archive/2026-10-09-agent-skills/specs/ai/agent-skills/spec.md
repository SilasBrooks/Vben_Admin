## Purpose

提供 Claude 式 Agent Skills 能力包管理：把「指导模型如何做事」的方法论（操作手册、写作规范、排查套路）做成管理员可维护的数据化技能，经渐进式披露（清单常驻、正文按需加载）动态扩展 AI 助手能力而不改代码。

## ADDED Requirements

### Requirement: 技能管理

系统 SHALL 提供技能管理接口（`/system/skill`）：分页查询（System:Skill:List）、新增（System:Skill:Add，幂等 + 限流）、编辑（System:Skill:Edit）、删除（System:Skill:Delete，审计）、启停（System:Skill:Edit）。每条技能 SHALL 包含：name（全局唯一，小写字母/数字/连字符，如 `announcement-writer`）、title（中文能力名）、description（给模型的场景描述：什么需求该用本技能）、content（技能正文，Markdown 指令）、enabled 启停、builtIn 内置标记。

约束 MUST 校验：name 格式与唯一性；description ≤300 字符；content ≤8000 字符；同时启用（enabled=1）的技能 MUST 不超过 20 条，超限拒绝启用操作。内置技能（builtIn=1）MUST 可编辑可停用但 MUST NOT 删除。

#### Scenario: name 格式校验
- **WHEN** 新增技能时 name 提交为「写公告」（非小写连字符格式）
- **THEN** 返回格式错误业务提示，不创建

#### Scenario: 启用数量上限
- **WHEN** 已启用 20 条技能时再启用第 21 条
- **THEN** 返回「启用技能数量已达上限」业务错误，状态不变

#### Scenario: 内置技能禁删
- **WHEN** 删除 builtIn=1 的内置技能
- **THEN** 返回业务错误，技能保留；停用操作正常执行

### Requirement: 内置示范技能

系统 SHALL 随种子数据内置至少 2 个示范技能并默认启用，使概念开箱可验证：「公告写作助手」（按站内公告规范起草公告正文，起草后引导使用公告发布能力）与「故障排查手册」（连接拒绝/服务不可用类问题的标准排查步骤）。内置技能随 DatabaseSeeder（空库）与存量库 upgrade SQL 双通道写入，内容 MUST 与实现文档保持一致。

#### Scenario: 空库自带技能
- **WHEN** 全新环境执行种子初始化
- **THEN** AI 技能页可见内置技能且处于启用状态，AI 对话可立即加载使用

### Requirement: 前端技能管理页面

前端 SHALL 在「系统管理」下提供 AI 技能页面（`/system/skill`）：列表（名称/title/描述摘要/启停/内置标记/时间）+ 新增/编辑弹窗（name/title/description 用表单 schema，content 用多行文本编辑并显示字数）+ 启停开关 + 删除（二次确认，内置技能删除按钮禁用），操作按钮以 System:Skill:Add/Edit/Delete 权限码控制。

#### Scenario: 正文编辑字数提示
- **WHEN** 编辑技能正文输入超过 8000 字符
- **THEN** 表单校验拦截并提示上限，不提交

#### Scenario: 无权限按钮隐藏
- **WHEN** 无 System:Skill:Delete 权限的账号进入该页面
- **THEN** 不渲染删除按钮
