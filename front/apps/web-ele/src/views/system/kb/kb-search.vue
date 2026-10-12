<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElEmpty, ElMessage, ElTag } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import type { KbSearchHit } from '#/api/system/kb';
import { searchKbApi } from '#/api/system/kb';
import { $t } from '#/locales';

/** 检索测试弹窗：与 AI 工具 search_knowledge 同链路，用于管理员验证混合召回效果 */
const [Modal, modalApi] = useVbenModal({
  onConfirm: handleSearch,
});

/** 匹配通道 → 标签色（双通道最相关为 success，仅全文为 warning，仅向量为默认） */
function matchTagType(matchType: KbSearchHit['matchType']) {
  if (matchType === 'both') {
    return 'success';
  }
  return matchType === 'fts' ? 'warning' : 'info';
}

const [Form, formApi] = useVbenForm({
  schema: [
    {
      fieldName: 'query',
      label: $t('kb.queryLabel'),
      component: 'Input',
      componentProps: {
        type: 'textarea',
        rows: 2,
        maxlength: 500,
        placeholder: $t('kb.queryPlaceholder'),
      },
      rules: 'required',
    },
    {
      fieldName: 'topK',
      label: $t('kb.topKLabel'),
      component: 'InputNumber',
      componentProps: {
        min: 1,
        max: 10,
        step: 1,
        controlsPosition: 'right',
      },
      defaultValue: 4,
    },
  ] as VbenFormSchema[],
  showDefaultActions: false,
});

const hits = ref<KbSearchHit[]>([]);
const searched = ref(false);

async function handleSearch() {
  const values = await formApi.getValues();
  const query = String(values.query ?? '').trim();
  if (!query) {
    return;
  }
  modalApi.lock();
  try {
    hits.value = await searchKbApi(query, (values.topK as number) || 4);
    searched.value = true;
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    ElMessage.error($t('kb.searchFailed', { reason }));
  } finally {
    modalApi.unlock();
  }
}
</script>

<template>
  <Modal class="w-[720px]" :title="$t('kb.searchTitle')">
    <Form />
    <div v-if="hits.length > 0" class="mt-2 space-y-2">
      <div
        v-for="hit in hits"
        :key="hit.id"
        class="border-border rounded-md border p-3 text-sm"
      >
        <div class="text-foreground/60 mb-1 flex items-center justify-between text-xs">
          <span>
            {{ hit.fileName }} · {{ $t('kb.chunkSeq') }} {{ hit.seq + 1 }}
          </span>
          <span class="flex items-center gap-2">
            <el-tag :type="matchTagType(hit.matchType)" size="small" effect="plain">
              {{ $t(`kb.match.${hit.matchType}`) }}
            </el-tag>
            <span>{{ $t('kb.rrfScore') }} {{ hit.score.toFixed(4) }}</span>
          </span>
        </div>
        <div class="whitespace-pre-wrap break-words">{{ hit.content }}</div>
      </div>
    </div>
    <ElEmpty
      v-else-if="searched"
      :description="$t('kb.searchEmpty')"
      :image-size="80"
    />
  </Modal>
</template>
