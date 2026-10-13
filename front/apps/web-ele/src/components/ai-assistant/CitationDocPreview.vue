<script lang="ts" setup>
import type { KbPreviewBlock } from '#/api/system/kb';

import { nextTick, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { previewKbDocApi } from '#/api/system/kb';
import { $t } from '#/locales';

/**
 * 溯源卡片「查看原文」预览弹窗：按块分隔展示文档正文（kb_chunk 切块存储形态），
 * 打开时自动滚动定位到引用块并高亮（一次性动画，离开视口无残留样式）。
 * 文档已删除/加载失败时展示可读错误，不阻塞对话面板。
 */
const loading = ref(false);
const errorText = ref('');
const fileName = ref('');
const blocks = ref<KbPreviewBlock[]>([]);
const targetSeq = ref(0);
const contentRef = ref<HTMLElement>();

const [Modal, modalApi] = useVbenModal({
  footer: false,
  onOpenChange(isOpen) {
    if (isOpen) {
      load();
    } else {
      errorText.value = '';
      blocks.value = [];
    }
  },
});

async function load() {
  const data = modalApi.getData<{ docId: number; seq: number }>();
  targetSeq.value = data?.seq ?? -1;
  loading.value = true;
  errorText.value = '';
  try {
    const doc = await previewKbDocApi(data.docId);
    fileName.value = doc.fileName;
    blocks.value = doc.blocks;
    await nextTick(scrollToTarget);
  } catch (error) {
    errorText.value =
      (error instanceof Error ? error.message : String(error))
      || $t('ai.citation.previewError');
  } finally {
    loading.value = false;
  }
}

/** 定位引用块：滚动到视口内并触发一次性高亮动画 */
function scrollToTarget() {
  if (targetSeq.value < 0 || !contentRef.value) return;
  const el = contentRef.value.querySelector<HTMLElement>(
    `[data-seq="${targetSeq.value}"]`,
  );
  if (!el) return;
  el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  el.classList.add('is-target');
  window.setTimeout(() => el.classList.remove('is-target'), 2400);
}
</script>

<template>
  <Modal class="w-[560px]" :title="fileName || $t('ai.citation.previewTitle')">
    <div v-if="errorText" class="ai-preview-error">{{ errorText }}</div>
    <div
      v-else
      ref="contentRef"
      v-loading="loading"
      class="max-h-[60vh] overflow-y-auto pr-1"
    >
      <p
        v-if="!loading && blocks.length === 0"
        class="text-muted-foreground py-6 text-center text-sm"
      >
        {{ $t('ai.citation.previewEmpty') }}
      </p>
      <div
        v-for="block in blocks"
        :key="block.seq"
        :data-seq="block.seq"
        class="kb-preview-block"
      >
        <div class="text-muted-foreground mb-1 text-xs">#{{ block.seq }}</div>
        <div class="whitespace-pre-wrap text-sm leading-6">{{ block.content }}</div>
      </div>
    </div>
  </Modal>
</template>

<style scoped>
.ai-preview-error {
  padding: 12px 0;
  font-size: 13px;
  color: hsl(var(--destructive));
}

.kb-preview-block {
  padding: 8px 10px;
  border-radius: 8px;
  transition: background-color 0.4s ease;
}

.kb-preview-block + .kb-preview-block {
  margin-top: 4px;
}

.kb-preview-block:hover {
  background: hsl(var(--accent));
}

/* 引用块定位高亮：一次性渐隐，不残留样式 */
.kb-preview-block.is-target {
  animation: cite-flash 2.4s ease;
}

@keyframes cite-flash {
  0%,
  40% {
    background: hsl(var(--primary) / 15%);
    box-shadow: inset 2px 0 0 hsl(var(--primary));
  }
  100% {
    background: transparent;
    box-shadow: none;
  }
}
</style>
