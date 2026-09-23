# Design

## Context

`MarkdownContent.vue` 以 `v-html` 注入渲染产物，模板层无法为 HTML 内的节点绑定 Vue 事件；代码块由 `markdown-it` 的 highlight 回调生成。自动滚动在 `AiAssistant.vue` 中由 `watch(messages, deep)` 触发。

## Goals / Non-Goals

**Goals**

- 代码块：语言标签 + 复制按钮 + 复制反馈，长代码（>20 行）默认折叠
- 自动滚动：跟随用户阅读位置，上翻不拉回
- 错误文案 i18n

**Non-Goals**

- 不做代码块编辑、下载、行号、换行切换等进阶功能
- 不改动流式节流策略（100ms 整体重渲维持现状）
- 不引入外部 markdown/highlight 插件

## Decisions

### D1：代码块 UI 由 fence renderer 注入，交互走事件委托

重写 `md.renderer.rules.fence`：输出 `<pre>` 外层结构内含 header 行（语言标签 + 复制按钮），而非依赖 highlight 回调。理由：

- highlight 回调仅负责 `<code>` 内容的转义与高亮，把 DOM 结构混进去职责不清
- fence renderer 可拿到 `token.info`（语言）与完整源码（可精确计算行数、折叠判定）

`v-html` 内节点无法绑定 Vue 事件 → 组件根元素用原生事件委托（`@click` 判断 `event.target.closest('.md-copy')` 等），复制动作读取对应 `<code>` 的 `textContent`，`navigator.clipboard.writeText` 写入，成功后给按钮加临时 class 显示「已复制」（纯 CSS 控制，1.5s 后移除）。

DOMPurify 默认允许 `button`/`span` 与 class 属性，无需额外配置；但需确认 sanitize 后 class 保留（DOMPurify 默认保留 class）。

### D2：折叠判定基于完整源码行数，流式中折叠但实时增长

fence renderer 阶段拿到的就是当前快照的完整 token 内容，行数判定天然随流式快照更新：超过 20 行的块输出 `data-fold="true"` 与展开按钮。流式中块持续增长跨过阈值时会自动进入折叠态——为避免「正在阅读的代码突然折叠」，折叠交互上**展开状态由用户点击后以 data-fold="false" 覆盖**，重渲后用户已展开的块会回到折叠态（可接受：流式中重渲频繁，但展开动作通常发生在流结束后；不引入跨重渲的状态记忆，保持实现简单）。

阈值 20 行；折叠态 `max-height: 180px` + 底部渐隐遮罩 + 「展开（N 行）」按钮。

### D3：自动滚动「底部跟随」判定

`scrollToBottom` 前判断：

```ts
const el = scrollRef.value;
const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 60;
if (nearBottom) el.scrollTop = el.scrollHeight;
```

- 新消息入列（用户发送、新气泡出现）仍强制滚底（用户主动发起对话，预期看到回复）
- 流式 delta 引发的内容增长仅在 nearBottom 时跟随
- 用户上翻超过阈值后停止跟随，滚回底部自动恢复

### D4：错误文案走既有语言包命名空间

`use-ai-chat.ts` 中 `error?.message || '执行失败，请稍后重试'` 的兜底文案改为 `$t('ai.chat.executeFailed')`（`#/locales` 的 `$t` 可在 ts 中使用，组件层已有先例）；`zh-CN` / `en-US` 的 `ai.ts` 同步补 key。

## Risks / Trade-offs

- 事件委托复制在流式重渲期间点击可能命中重建中的 DOM → 复制动作按点击瞬间的 `textContent` 取值，无竞态危害
- 流式中已展开的块重渲后回到折叠态（D2 取舍），保证实现简单；若实测体验差再引入状态记忆
- clipboard API 在非 HTTPS / 非 localhost 环境不可用 → 降级 `document.execCommand('copy')` 兜底
