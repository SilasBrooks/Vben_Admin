# Tasks

## 实现

- [x] 1. `MarkdownContent.vue`：重写 `fence` renderer——输出语言标签 + 复制按钮 + 折叠结构（>20 行 `data-fold`），highlight 回调仅保留 `<code>` 内容高亮职责
- [x] 2. `MarkdownContent.vue`：根元素事件委托——复制（clipboard API + execCommand 兜底 + 已复制反馈）、展开/收起切换
- [x] 3. `MarkdownContent.vue`：折叠/展开与代码块 header 样式（折叠态 max-height + 底部渐隐 + 「展开（N 行）」），暗色主题适配
- [x] 4. `AiAssistant.vue`：滚动改为底部跟随判定（阈值 60px）；用户发送消息后仍强制滚底
- [x] 5. `use-ai-chat.ts`：执行失败兜底文案改 `$t('ai.chat.executeFailed')`；`locales/langs/{zh-CN,en-US}/ai.ts` 同步补 key

## 验证

- [x] 6. `front/` 下 `pnpm check:type` 通过
- [x] 7. 浏览器实测：带语言代码块显示语言标签，复制按钮写入剪贴板且出现「已复制」反馈；25 行长代码默认折叠、展开/收起正常；流式输出中上翻历史消息不被拉回、滚回底部恢复跟随；英文界面下执行失败提示为英文；XSS 防护不受影响（button 注入内容不逃逸）

## 收尾

- [x] 8. 同步根 README.md（AI 助手体验增强一句话）
- [x] 9. 归档 openspec 变更（sync delta → archive）
