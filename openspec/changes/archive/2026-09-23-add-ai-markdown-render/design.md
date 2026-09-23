# 设计：AI 助手 Markdown 流式渲染

## Context

- 现状：`AiAssistant.vue` 助手气泡以 `<span class="ai-bubble__text">{{ msg.content }}</span>` 纯文本渲染（`white-space: pre-wrap`）；SSE delta 直接累加到 `msg.content`（`use-ai-chat.ts` 的 `onDelta`）。
- `front/packages/` 无现成 markdown 封装（仅 tiptap highlight，与富文本编辑器绑定，不适用），需在 `apps/web-ele` 自行引入依赖。
- 确认卡/计划卡为受控组件渲染（`msg.cards` / `msg.planCards`），与正文渲染解耦。

## Goals / Non-Goals

**Goals**

- 助手正文 Markdown 渲染 + 代码高亮，风格与现有 indigo/violet 渐变主题、Element Plus CSS 变量协调
- 模型输出零信任：解析层禁透传 + sanitize 层兜底
- 流式不卡顿：节流重渲，结束必达最终态

**Non-Goals**

- 不做编辑器（tiptap）、不做数学公式/Mermaid、不做代码块复制按钮与主题切换
- 不改后端、不改 SSE 协议、不新增 i18n key

## Decisions

### D1 依赖选型：markdown-it + dompurify + highlight.js（core 子集）

- `markdown-it` ^14：CommonMark 规范、容错性好（未闭合围栏天然容忍）、生态成熟
- `dompurify` ^3：sanitize 事实标准
- `highlight.js` ^11：只引 `highlight.js/lib/core` 并注册常用语言子集（java/ts/js/sql/json/bash/xml/css/yaml/python/markdown），避免全量语言包（~1MB）进 bundle
- 安装于 `front/apps/web-ele`（业务应用自治依赖），dev 依赖补 `@types/markdown-it`

### D2 双重 XSS 防线

1. markdown-it 实例 `html: false`：模型输出中的原始 HTML 标签一律转义为文本
2. 渲染产物统一过 `DOMPurify.sanitize()` 再 `v-html`（防御 href 伪协议等残留面）；`linkify: true` 识别裸 URL，链接消毒后补 `rel="noopener noreferrer"`

`v-html` 仅用于助手正文；用户气泡、错误气泡、卡片文案全部保持插值渲染。

### D3 流式渲染策略：累积重渲 + 定时节流（非增量 DOM）

- 新组件 `MarkdownContent.vue`：props `{ content: string; streaming?: boolean }`
- 内部维护 `renderedContent`（参与渲染的文本快照）：`streaming` 期间每 ~100ms 同步一次 `props.content` 并重渲整体 HTML；`streaming` 变 false 时立即同步并渲染（保证 done/stop/error 后最终态必达）
- 理由：markdown 解析是有状态整体操作，增量 DOM 补丁需自维护解析树，复杂度高且易错；整段重渲对对话长度（单条 ≤ 数 KB）开销可忽略，节流 100ms 后每秒最多 10 次解析，肉眼流畅
- 卸载时清理定时器，防泄漏

### D4 组件接入

`AiAssistant.vue` 中 `<span v-else-if="msg.content" class="ai-bubble__text">{{ msg.content }}</span>` 替换为 `<MarkdownContent v-else-if="msg.content" :content="msg.content" :streaming="msg.streaming" />`；流式光标 `ai-cursor` 保留在气泡尾部。`is-error` 气泡不走 MarkdownContent，维持纯文本红色样式。

### D5 样式协调

- 组件根 `.markdown-content`，scoped + `:deep()` 覆盖 p/ul/ol/table/code/pre/h1-h4/blockquote/a 等（气泡宽度有限，标题降级到 h4 尺寸）
- 颜色全部走 Element Plus CSS 变量（`--el-text-color-*` / `--el-border-color-*` / `--el-fill-color-*`），暗色主题自动适配
- 代码块：深色底（固定 `#0d1117` 系）+ 圆角 + 横向滚动，hljs 配色用 github-dark 风格自写精简变量集，不引整包 CSS 主题

## Risks / Trade-offs

- 未闭合围栏期间围栏后内容暂渲染为代码块 → 可接受（ChatGPT 同款行为），闭合后自动纠正
- `breaks: true`（单换行渲染 `<br>`）更贴近聊天语境，但与严格 CommonMark 有偏差 → 对话场景利大于弊
- v-html 内容不含 Vue 响应式，卡片/按钮在正文之外独立渲染，无交互冲突
- highlight.js 语言子集遇未注册语言时降级为无高亮纯文本，不报错

## Migration Plan

纯新增渲染层，无数据迁移；依赖安装后 HMR 即生效。

## Open Questions

（无）
