# delta: ai/assistant

## ADDED Requirements

### Requirement: Markdown 渲染与 XSS 防护

助手消息内容 MUST 以 Markdown 渲染展示，至少支持：标题、有序/无序列表、表格、行内代码、代码块（带语法高亮）、链接、引用、粗体/斜体。用户气泡 MUST 保持纯文本渲染，MUST NOT 对用户输入应用 HTML 渲染。

模型输出不可信：Markdown 渲染 MUST 遵循双重防线——解析器 MUST 禁用原始 HTML 透传（模型输出中的 HTML 标签按转义文本展示），渲染产物 MUST 经 DOMPurify 消毒后方可通过 `v-html` 注入 DOM。

流式输出期间 MUST 采用节流重渲策略（整体重渲 + 定时刷新），MUST NOT 对每个 delta 片段单独执行完整解析；本轮流结束（done/stop/error）时 MUST 以完整文本执行一次最终渲染。流式过程中出现未闭合的代码围栏 MUST 容忍（按容错规则渲染），MUST NOT 报错或中断渲染，文本完整后渲染结果自动纠正。

确认卡片与计划卡片的展示、交互 MUST NOT 受 Markdown 渲染影响；错误提示气泡 MUST 保持纯文本样式。

#### Scenario: Markdown 正常渲染

- **WHEN** 模型回复包含标题、列表、表格与行内代码
- **THEN** 气泡内按 Markdown 样式渲染（层级标题、列表符号、表格线框、行内代码底色），不出现原始 Markdown 符号

#### Scenario: 代码块语法高亮

- **WHEN** 模型回复包含带语言标注的代码块（如 ```sql）
- **THEN** 代码块以高亮配色渲染，与对话主题风格协调

#### Scenario: 恶意输出被消毒

- **WHEN** 诱导模型回复中包含 `<script>alert(1)</script>` 或 `<img onerror>` 等内容
- **THEN** 页面不执行任何脚本、不弹窗，相关内容以转义文本或被剥离后的安全节点展示

#### Scenario: 流式未闭合围栏容忍

- **WHEN** 流式输出过程中代码围栏尚未闭合
- **THEN** 渲染持续进行不报错（围栏后内容按代码块容错渲染），围栏闭合后渲染自动纠正

#### Scenario: 确认卡与计划卡不受影响

- **WHEN** 助手消息同时包含 Markdown 正文与确认卡片/计划卡
- **THEN** 正文按 Markdown 渲染，卡片标题、参数表格与按钮行为与既有交互完全一致

#### Scenario: 用户气泡保持纯文本

- **WHEN** 用户输入包含 `<b>bold</b>`、`*斜体*` 等 Markdown/HTML 语法
- **THEN** 用户气泡按输入原文展示，不做任何 HTML 渲染
