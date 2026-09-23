# Tasks

## 实现

- [x] 1. `front/apps/web-ele` 安装依赖：`markdown-it`、`dompurify`、`highlight.js`（运行时）+ `@types/markdown-it`（dev）
- [x] 2. 新建 `src/components/ai-assistant/MarkdownContent.vue`：markdown-it（`html:false`、`linkify`、`breaks`）+ DOMPurify 消毒 + highlight.js core 语言子集 + 流式 100ms 节流重渲、结束强制最终渲染
- [x] 3. `AiAssistant.vue` 接入：助手正文气泡替换为 `MarkdownContent`，错误气泡/用户气泡保持纯文本，确认卡/计划卡与流式光标不动
- [x] 4. 样式：`.markdown-content` 覆盖常用元素（标题/列表/表格/行内代码/代码块/链接/引用），Element Plus CSS 变量 + hljs 精简暗色配色，暗色主题适配

## 验证

- [x] 5. `front/` 下 `pnpm check:type` 通过
- [x] 6. 浏览器实测：普通 Markdown（标题/列表/表格/行内代码）渲染正确；带语言代码块高亮；XSS 用例（诱导输出 `<script>`/`onerror`）不执行；未闭合 \`\`\` 流式期间不报错且闭合后自动纠正；确认卡/计划卡交互不受影响；流式输出流畅无卡顿

## 收尾

- [x] 7. 归档 openspec 变更（sync delta → archive）
