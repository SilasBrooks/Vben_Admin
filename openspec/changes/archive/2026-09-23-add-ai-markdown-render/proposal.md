## Why

AI 助手回复当前以纯文本（`white-space: pre-wrap`）展示，模型输出的 Markdown（标题、列表、表格、代码块）全部以原始符号呈现，可读性差；代码片段无高亮，表格退化为断裂的竖线文本。主流 AI 对话产品均以 Markdown 渲染回复，本变更是优化队列的第一项。

## What Changes

- 助手气泡内容改为 Markdown 渲染：引入 markdown-it 解析，支持标题/列表/表格/代码块/行内代码/链接/引用等常用元素
- 代码块语法高亮：highlight.js（core + 常用语言子集注册，控制体积）
- XSS 防护：模型输出不可信，渲染结果 MUST 经 DOMPurify 消毒后再 `v-html`；markdown-it 关闭原始 HTML 透传（`html: false`）作为第一道防线
- 流式渐进渲染：SSE delta 累积文本整体重渲 + 定时节流（避免每个 delta 触发完整解析），流结束后强制补一次最终渲染
- 未闭合代码围栏容忍：流式过程中出现未闭合 \``` 时按既有容错渲染（markdown-it 天然容忍），最终文本完整后自动纠正，不报错
- 用户气泡、错误气泡、确认卡片、计划卡逻辑不受影响（纯渲染层改动，后端无变更）

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `ai/assistant`: 新增「Markdown 渲染与 XSS 防护」需求——助手消息 MUST 以 Markdown 渲染且经 sanitize，流式渲染 MUST 节流，确认卡/计划卡展示不受影响

## Impact

- 前端 `front/apps/web-ele`：新增 `src/components/ai-assistant/MarkdownContent.vue`；修改 `AiAssistant.vue` 气泡渲染处
- 依赖：`apps/web-ele` 新增 `markdown-it`、`dompurify`、`highlight.js`（运行时）与 `@types/markdown-it`（开发）
- 后端、协议、表结构、i18n key、权限码：均无变更
