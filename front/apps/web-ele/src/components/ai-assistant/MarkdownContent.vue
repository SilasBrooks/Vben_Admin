<script lang="ts" setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue';

import DOMPurify from 'dompurify';
import hljs from 'highlight.js/lib/core';
import bash from 'highlight.js/lib/languages/bash';
import css from 'highlight.js/lib/languages/css';
import java from 'highlight.js/lib/languages/java';
import javascript from 'highlight.js/lib/languages/javascript';
import json from 'highlight.js/lib/languages/json';
import markdownLang from 'highlight.js/lib/languages/markdown';
import python from 'highlight.js/lib/languages/python';
import sql from 'highlight.js/lib/languages/sql';
import typescript from 'highlight.js/lib/languages/typescript';
import xml from 'highlight.js/lib/languages/xml';
import yaml from 'highlight.js/lib/languages/yaml';
import MarkdownIt from 'markdown-it';

import { $t } from '#/locales';

// 常用语言子集（注册即含官方别名：js/ts/py/html/sh/yml/md 等），控制 bundle 体积
hljs.registerLanguage('javascript', javascript);
hljs.registerLanguage('typescript', typescript);
hljs.registerLanguage('java', java);
hljs.registerLanguage('python', python);
hljs.registerLanguage('sql', sql);
hljs.registerLanguage('json', json);
hljs.registerLanguage('bash', bash);
hljs.registerLanguage('xml', xml);
hljs.registerLanguage('css', css);
hljs.registerLanguage('yaml', yaml);
hljs.registerLanguage('markdown', markdownLang);

/** 长代码折叠阈值（行数） */
const FOLD_LINES = 20;

/**
 * markdown-it 实例（模型输出不可信）：
 * - html: false —— 原始 HTML 一律转义为文本，第一道防线
 * - linkify + breaks —— 裸 URL 成链接、单换行成 <br>，贴近聊天语境
 * - fence 自定义渲染：语言标签 + 复制按钮 + 长代码折叠（交互走事件委托）
 */
const md: MarkdownIt = new MarkdownIt({
  breaks: true,
  html: false,
  linkify: true,
});

/** 代码高亮（调用前需确认语言已注册），失败降级为转义文本 */
function highlightCode(source: string, lang: string): string {
  try {
    return hljs.highlight(source, { language: lang }).value;
  } catch {
    return md.utils.escapeHtml(source);
  }
}

// fence：输出 head（语言标签 + 折叠/复制按钮）+ pre 结构
md.renderer.rules.fence = (tokens, idx) => {
  const token = tokens[idx]!;
  const lang = (token.info.trim().match(/^\S*/)?.[0] ?? '').toLowerCase();
  const source = token.content;
  const lines = source.endsWith('\n')
    ? source.split('\n').length - 1
    : source.split('\n').length;
  const body = lang && hljs.getLanguage(lang)
    ? highlightCode(source, lang)
    : md.utils.escapeHtml(source);
  const foldable = lines > FOLD_LINES;
  const foldBtn = foldable
    ? `<button class="md-fold" data-fold="true" type="button">${$t('ai.chat.expand', [lines])}</button>`
    : '';
  return (
    `<div class="md-code" data-lines="${lines}">` +
    `<div class="md-code-head">` +
    `<span class="md-code-lang">${md.utils.escapeHtml(lang || 'text')}</span>` +
    `<span class="md-code-actions">${foldBtn}` +
    `<button class="md-copy" type="button">${$t('ai.chat.copy')}</button>` +
    `</span></div>` +
    `<pre class="hljs"${foldable ? ' data-fold="true"' : ''}><code>${body}</code></pre>` +
    `</div>`
  );
};

// 第二道防线：渲染产物统一过 DOMPurify；链接统一新开页 + noopener
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A') {
    node.setAttribute('rel', 'noopener noreferrer');
    node.setAttribute('target', '_blank');
  }
});

function toSafeHtml(text: string): string {
  return DOMPurify.sanitize(md.render(text));
}

const props = defineProps<{
  content: string;
  /** 流式输出中：节流重渲；结束/非流式：立即重渲 */
  streaming?: boolean;
}>();

/** 参与渲染的文本快照（流式期间按节流周期从 props 同步） */
const renderedText = ref(props.content);

const html = computed(() => toSafeHtml(renderedText.value));

/** 复制：clipboard API 优先，失败（含非安全上下文/权限受限）降级 execCommand */
async function copyText(text: string, btn: HTMLElement) {
  let ok = false;
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      ok = true;
    }
  } catch {
    ok = false;
  }
  if (!ok) {
    try {
      const ta = document.createElement('textarea');
      ta.value = text;
      ta.style.position = 'fixed';
      ta.style.opacity = '0';
      document.body.append(ta);
      ta.select();
      ok = document.execCommand('copy');
      ta.remove();
    } catch {
      ok = false;
    }
  }
  if (!ok) return;
  btn.textContent = $t('ai.chat.copied');
  btn.classList.add('is-copied');
  copiedTimer = setTimeout(() => {
    copiedTimer = null;
    // 流式重渲可能已重建 DOM，仅恢复仍挂载的按钮
    if (btn.isConnected) {
      btn.textContent = $t('ai.chat.copy');
      btn.classList.remove('is-copied');
    }
  }, 1500);
}

/** v-html 内节点无法绑 Vue 事件：根元素统一事件委托（复制/展开收起） */
function onRootClick(event: MouseEvent) {
  const target = event.target as HTMLElement;
  const copyBtn = target.closest('.md-copy') as HTMLElement | null;
  if (copyBtn) {
    const code = copyBtn.closest('.md-code')?.querySelector('pre code');
    void copyText(code?.textContent ?? '', copyBtn);
    return;
  }
  const foldBtn = target.closest('.md-fold') as HTMLElement | null;
  if (foldBtn) {
    const box = foldBtn.closest('.md-code');
    const pre = box?.querySelector('pre');
    if (!pre) return;
    const folded = pre.getAttribute('data-fold') === 'true';
    pre.setAttribute('data-fold', folded ? 'false' : 'true');
    foldBtn.setAttribute('data-fold', folded ? 'false' : 'true');
    const lines = Number(box?.getAttribute('data-lines') ?? 0);
    foldBtn.textContent = folded
      ? $t('ai.chat.collapse')
      : $t('ai.chat.expand', [lines]);
  }
}

let copiedTimer: null | ReturnType<typeof setTimeout> = null;

let timer: null | ReturnType<typeof setTimeout> = null;

function flush() {
  timer = null;
  if (renderedText.value !== props.content) {
    renderedText.value = props.content;
  }
}

watch(
  () => props.content,
  (val) => {
    if (!props.streaming) {
      renderedText.value = val;
      return;
    }
    // 流式中 ~100ms 节流整体重渲，避免每个 delta 触发完整解析
    if (timer === null) {
      timer = setTimeout(flush, 100);
    }
  },
);

watch(
  () => props.streaming,
  (streaming) => {
    if (!streaming) {
      // 结束（done/stop/error）：取消未触发的节流并立即最终渲染
      if (timer !== null) {
        clearTimeout(timer);
        timer = null;
      }
      renderedText.value = props.content;
    }
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  if (timer !== null) clearTimeout(timer);
  if (copiedTimer !== null) clearTimeout(copiedTimer);
});
</script>

<template>
  <div class="markdown-content" v-html="html" @click="onRootClick" />
</template>

<style scoped>
.markdown-content {
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
}
.markdown-content :deep(p) {
  margin: 0 0 8px;
}
.markdown-content :deep(p:last-child) {
  margin-bottom: 0;
}

/* 标题：气泡宽度有限，整体降级 */
.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  margin: 10px 0 6px;
  font-weight: 600;
  line-height: 1.4;
}
.markdown-content :deep(h1) {
  font-size: 17px;
}
.markdown-content :deep(h2) {
  font-size: 16px;
}
.markdown-content :deep(h3) {
  font-size: 15px;
}
.markdown-content :deep(h4),
.markdown-content :deep(h5),
.markdown-content :deep(h6) {
  font-size: 14px;
}
.markdown-content :deep(h1:first-child),
.markdown-content :deep(h2:first-child),
.markdown-content :deep(h3:first-child),
.markdown-content :deep(h4:first-child) {
  margin-top: 0;
}

/* 列表 */
.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 4px 0 8px;
  padding-left: 20px;
}
.markdown-content :deep(li) {
  margin: 2px 0;
}

/* 引用 */
.markdown-content :deep(blockquote) {
  margin: 6px 0;
  padding: 4px 10px;
  border-left: 3px solid #8b5cf6;
  background: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-text-color-regular, #606266);
  border-radius: 4px;
}

/* 链接与分隔线 */
.markdown-content :deep(a) {
  color: var(--el-color-primary, #6366f1);
  text-decoration: none;
}
.markdown-content :deep(a:hover) {
  text-decoration: underline;
}
.markdown-content :deep(hr) {
  border: none;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
  margin: 10px 0;
}

/* 行内代码 */
.markdown-content :deep(code) {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--el-fill-color, #f0f2f5);
  font-size: 12.5px;
  font-family: ui-monospace, SFMono-Regular, Consolas, 'Courier New', monospace;
}

/* 代码块容器：深色底 + head 行（语言标签 + 操作按钮） */
.markdown-content :deep(.md-code) {
  margin: 8px 0;
  border-radius: 8px;
  background: #0d1117;
  overflow: hidden;
}
.markdown-content :deep(.md-code-head) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 5px 10px;
  background: #161b22;
  font-size: 12px;
  line-height: 1.4;
}
.markdown-content :deep(.md-code-lang) {
  color: #8b949e;
  font-family: ui-monospace, SFMono-Regular, Consolas, 'Courier New', monospace;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.markdown-content :deep(.md-code-actions) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.markdown-content :deep(.md-copy),
.markdown-content :deep(.md-fold) {
  border: none;
  background: transparent;
  padding: 2px 6px;
  border-radius: 4px;
  color: #c9d1d9;
  font-size: 12px;
  line-height: 1.4;
  cursor: pointer;
  transition:
    background 0.15s,
    color 0.15s;
}
.markdown-content :deep(.md-copy:hover),
.markdown-content :deep(.md-fold:hover) {
  background: rgb(255 255 255 / 12%);
  color: #fff;
}
.markdown-content :deep(.md-copy.is-copied) {
  color: #7ee787;
}

/* 代码块：横向滚动（margin/radius 由容器接管） */
.markdown-content :deep(pre) {
  margin: 0;
  padding: 10px 12px;
  background: #0d1117;
  color: #e6edf3;
  overflow-x: auto;
  font-size: 12.5px;
  line-height: 1.6;
}
.markdown-content :deep(pre::-webkit-scrollbar) {
  height: 5px;
}
.markdown-content :deep(pre::-webkit-scrollbar-thumb) {
  background: #30363d;
  border-radius: 3px;
}
.markdown-content :deep(pre code) {
  display: block;
  padding: 0;
  background: transparent;
  color: inherit;
  font-size: inherit;
  white-space: pre;
  word-break: normal;
  overflow-wrap: normal;
}

/* 长代码折叠态：限高 + 底部渐隐遮罩 */
.markdown-content :deep(pre[data-fold='true']) {
  max-height: 180px;
  overflow: hidden;
  position: relative;
}
.markdown-content :deep(pre[data-fold='true']::after) {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 48px;
  background: linear-gradient(to bottom, transparent, #0d1117);
  pointer-events: none;
}

/* 表格：窄气泡内横向滚动 */
.markdown-content :deep(table) {
  display: block;
  max-width: 100%;
  margin: 8px 0;
  border-collapse: collapse;
  font-size: 13px;
  overflow-x: auto;
}
.markdown-content :deep(th),
.markdown-content :deep(td) {
  padding: 4px 10px;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  text-align: left;
}
.markdown-content :deep(th) {
  background: var(--el-fill-color-light, #f5f7fa);
  font-weight: 600;
  white-space: nowrap;
}

/* highlight.js 精简配色（github-dark 系） */
.markdown-content :deep(.hljs-comment),
.markdown-content :deep(.hljs-quote) {
  color: #8b949e;
  font-style: italic;
}
.markdown-content :deep(.hljs-keyword),
.markdown-content :deep(.hljs-selector-tag),
.markdown-content :deep(.hljs-meta .hljs-keyword),
.markdown-content :deep(.hljs-doctag),
.markdown-content :deep(.hljs-name) {
  color: #ff7b72;
}
.markdown-content :deep(.hljs-string),
.markdown-content :deep(.hljs-regexp),
.markdown-content :deep(.hljs-addition) {
  color: #a5d6ff;
}
.markdown-content :deep(.hljs-attr),
.markdown-content :deep(.hljs-attribute),
.markdown-content :deep(.hljs-variable),
.markdown-content :deep(.hljs-template-variable),
.markdown-content :deep(.hljs-property) {
  color: #7ee787;
}
.markdown-content :deep(.hljs-number),
.markdown-content :deep(.hljs-literal),
.markdown-content :deep(.hljs-symbol),
.markdown-content :deep(.hljs-bullet),
.markdown-content :deep(.hljs-link) {
  color: #79c0ff;
}
.markdown-content :deep(.hljs-title),
.markdown-content :deep(.hljs-title.function_),
.markdown-content :deep(.hljs-section) {
  color: #d2a8ff;
}
.markdown-content :deep(.hljs-type),
.markdown-content :deep(.hljs-class .hljs-title),
.markdown-content :deep(.hljs-built_in) {
  color: #ffa657;
}
.markdown-content :deep(.hljs-meta),
.markdown-content :deep(.hljs-comment.hljs-meta) {
  color: #8b949e;
}
.markdown-content :deep(.hljs-deletion) {
  color: #ffa198;
}
.markdown-content :deep(.hljs-emphasis) {
  font-style: italic;
}
.markdown-content :deep(.hljs-strong) {
  font-weight: 600;
}
</style>
