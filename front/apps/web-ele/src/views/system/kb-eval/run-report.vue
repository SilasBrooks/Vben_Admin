<script lang="ts" setup>
import type { KbEvalRunItem } from '#/api/system/kbEval';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElAlert, ElEmpty, ElTag } from 'element-plus';

import { getKbEvalRunApi } from '#/api/system/kbEval';
import { $t } from '#/locales';

const [Modal, modalApi] = useVbenModal({
  footer: false,
  onOpenChange(isOpen: boolean) {
    if (isOpen) {
      init();
    }
  },
});

const run = ref<KbEvalRunItem | null>(null);
const loading = ref(false);

async function init() {
  const data = modalApi.getData<{ runId: number }>();
  loading.value = true;
  try {
    run.value = await getKbEvalRunApi(data.runId);
  } finally {
    loading.value = false;
  }
}

function fmtMetric(value?: null | number) {
  return value === null || value === undefined ? '-' : value.toFixed(4);
}
</script>

<template>
  <Modal class="w-[860px]" :title="$t('kbEval.reportTitle')">
    <div v-loading="loading" class="flex flex-col gap-3">
      <template v-if="run">
        <div class="flex flex-wrap items-center gap-2">
          <ElTag size="small">{{ run.setName }}</ElTag>
          <ElTag size="small" type="info">Top-K={{ run.topK }}</ElTag>
          <ElTag size="small" type="info">{{ $t('kbEval.casesCount', { n: run.totalCases }) }}</ElTag>
          <ElTag
            size="small"
            :type="run.rerankActive ? 'warning' : 'info'"
            :effect="run.rerankActive ? 'dark' : 'plain'"
          >
            {{ run.rerankActive ? $t('kbEval.rerankOn') : $t('kbEval.rerankOff') }}
          </ElTag>
          <ElTag size="small" type="info" effect="plain">
            {{ $t('kbEval.embeddingModel', { model: run.embeddingModel || '-' }) }}
          </ElTag>
          <ElTag size="small" type="info" effect="plain">{{ run.elapsedMs }}ms</ElTag>
          <span class="text-muted-foreground text-xs">{{ run.createTime }}</span>
        </div>

        <div class="grid grid-cols-4 gap-3">
          <div class="bg-accent rounded-md p-3 text-center">
            <div class="text-muted-foreground text-xs">Hit@1</div>
            <div class="text-primary text-xl font-semibold">{{ fmtMetric(run.hitAt1) }}</div>
          </div>
          <div class="bg-accent rounded-md p-3 text-center">
            <div class="text-muted-foreground text-xs">Hit@3</div>
            <div class="text-primary text-xl font-semibold">{{ fmtMetric(run.hitAt3) }}</div>
          </div>
          <div class="bg-accent rounded-md p-3 text-center">
            <div class="text-muted-foreground text-xs">Hit@5</div>
            <div class="text-primary text-xl font-semibold">{{ fmtMetric(run.hitAt5) }}</div>
          </div>
          <div class="bg-accent rounded-md p-3 text-center">
            <div class="text-muted-foreground text-xs">MRR</div>
            <div class="text-primary text-xl font-semibold">{{ fmtMetric(run.mrr) }}</div>
          </div>
        </div>

        <ElAlert :title="$t('kbEval.metricHint')" type="info" :closable="false" show-icon />

        <div class="flex flex-col gap-2 overflow-y-auto" style="max-height: 420px">
          <div v-for="(item, index) in run.detail" :key="index" class="bg-accent rounded-md p-3">
            <div class="mb-1 flex items-center gap-2">
              <ElTag size="small" type="info">#{{ index + 1 }}</ElTag>
              <span class="text-sm">{{ item.query }}</span>
              <ElTag
                v-if="item.rank"
                size="small"
                type="success"
              >
                {{ $t('kbEval.rankLabel', { rank: item.rank }) }}
              </ElTag>
              <ElTag v-else size="small" type="danger">{{ $t('kbEval.missLabel') }}</ElTag>
              <span class="text-muted-foreground ml-auto text-xs">
                {{ $t('kbEval.expectLabel', { doc: item.expectDocName || item.expectDocId, seq: item.expectSeq ?? '-' }) }}
              </span>
            </div>
            <div v-if="item.rank" class="flex flex-col gap-1">
              <div
                v-for="(hit, hIndex) in item.topHits"
                :key="hIndex"
                class="flex items-center gap-2 text-xs"
              >
                <span
                  class="w-8 shrink-0 text-center"
                  :class="hIndex + 1 === item.rank ? 'text-primary font-semibold' : 'text-muted-foreground'"
                >
                  #{{ hIndex + 1 }}
                </span>
                <span class="w-40 shrink-0 truncate" :class="hIndex + 1 === item.rank ? 'text-primary' : ''">
                  {{ hit.fileName }}
                </span>
                <span class="text-muted-foreground">seq={{ hit.seq }}</span>
                <span class="text-muted-foreground">
                  {{ $t('kbEval.fusionScore', { score: hit.score?.toFixed(4) }) }}
                </span>
                <span v-if="hit.rerankScore !== null && hit.rerankScore !== undefined" class="text-primary">
                  {{ $t('kbEval.rerankScore', { score: hit.rerankScore.toFixed(4) }) }}
                </span>
              </div>
            </div>
          </div>
          <ElEmpty
            v-if="!run.detail || run.detail.length === 0"
            :description="$t('kbEval.noDetail')"
          />
        </div>
      </template>
    </div>
  </Modal>
</template>
