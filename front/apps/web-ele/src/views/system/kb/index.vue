<script lang="ts" setup>
import type { UploadRequestOptions } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { KbDocItem } from '#/api/system/kb';

import { computed, onUnmounted, ref } from 'vue';

import { Page, VbenButton, useVbenModal } from '@vben/common-ui';

import { ElMessage, ElMessageBox, ElProgress, ElTag, ElTooltip, ElUpload } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  completeKbUploadApi,
  deleteKbDocApi,
  getKbDocListApi,
  initKbUploadApi,
  retryKbDocApi,
  uploadKbChunkApi,
  uploadKbDocApi,
} from '#/api/system/kb';
import { $t } from '#/locales';

import KbSearchModal from './kb-search.vue';
import { IncrementalSha256 } from './sha256';

const [SearchModal, searchModalApi] = useVbenModal({
  connectedComponent: KbSearchModal,
});

function openSearch() {
  searchModalApi.setData({}).open();
}

// ------------------------------------------------------------------
// 上传（ElUpload 托管文件选择 + http-request 自定义上传）
// 双路径选路（设计 D1）：≤10MB 单请求直传；>10MB 分片协议
// （增量 SHA-256 → init → 并发分片 → complete；失败保留会话支持断点续传）
// ------------------------------------------------------------------

/** 与后端 KbDocService.MAX_SIZE_BYTES 对齐的单请求路径上限 */
const SINGLE_REQUEST_LIMIT = 10 * 1024 * 1024;
/** 分片单片大小：5MB（后端约束 1-10MB；200MB/5MB=40 片恰为片数上限） */
const CHUNK_SIZE = 5 * 1024 * 1024;
/** 并发分片数 */
const CHUNK_CONCURRENCY = 3;
/** 单片失败重试次数 */
const CHUNK_RETRY = 3;
/** 哈希计算每次读取的块大小（内存常数） */
const HASH_READ_SIZE = 8 * 1024 * 1024;

const uploading = ref(false);
/** 上传进度（0-100）：哈希阶段按读取字节数、分片阶段按完成片数折算 */
const uploadPercent = ref(0);

async function onDocUpload(options: UploadRequestOptions) {
  const file = options.file as File;
  uploading.value = true;
  uploadPercent.value = 0;
  try {
    if (file.size > SINGLE_REQUEST_LIMIT) {
      await uploadLargeFile(file);
    } else {
      await uploadKbDocApi(file);
    }
    ElMessage.success($t('kb.uploadAccepted'));
    gridApi.query();
  } finally {
    uploading.value = false;
    uploadPercent.value = 0;
  }
}

/** 分块读取文件计算 SHA-256（进度 0-30%，为分片阶段预留 30-100%） */
async function computeFileSha256(file: File): Promise<string> {
  const hasher = new IncrementalSha256();
  for (let offset = 0; offset < file.size; offset += HASH_READ_SIZE) {
    const buf = await file.slice(offset, offset + HASH_READ_SIZE).arrayBuffer();
    hasher.update(new Uint8Array(buf));
    uploadPercent.value = Math.round(
      (Math.min(offset + HASH_READ_SIZE, file.size) / file.size) * 30,
    );
  }
  return hasher.hex();
}

/** 单片上传：失败按 1s/2s 退避重试，重试耗尽向上抛（保留会话断点续传） */
async function uploadChunkWithRetry(uploadId: string, index: number, blob: Blob) {
  for (let attempt = 1; ; attempt++) {
    try {
      await uploadKbChunkApi(uploadId, index, blob);
      return;
    } catch (error) {
      if (attempt >= CHUNK_RETRY) {
        throw error;
      }
      await new Promise((resolve) => setTimeout(resolve, attempt * 1000));
    }
  }
}

async function uploadLargeFile(file: File) {
  const sha256 = await computeFileSha256(file);
  const init = await initKbUploadApi({
    chunkSize: CHUNK_SIZE,
    fileName: file.name,
    fileSize: file.size,
    sha256,
  });
  // 秒传：服务器已有同内容文件，零上传直接建文档
  if (init.instant) {
    ElMessage.success($t('kb.instantHit'));
    return;
  }
  const uploadId = init.uploadId!;
  const totalChunks = init.totalChunks!;
  const pending = Array.from({ length: totalChunks }, (_, i) => i).filter(
    (i) => !(init.receivedChunks ?? []).includes(i),
  );
  if (pending.length < totalChunks) {
    ElMessage.info(
      $t('kb.resumeDetected', { done: totalChunks - pending.length, total: totalChunks }),
    );
  }

  // 并发消费分片队列：完成一片推进进度（30-100% 区间折算）
  let completed = totalChunks - pending.length;
  const workers = Array.from(
    { length: Math.min(CHUNK_CONCURRENCY, pending.length) },
    () =>
      (async () => {
        while (pending.length > 0) {
          const index = pending.shift()!;
          const start = index * CHUNK_SIZE;
          const end = Math.min(start + CHUNK_SIZE, file.size);
          await uploadChunkWithRetry(uploadId, index, file.slice(start, end));
          completed += 1;
          uploadPercent.value = 30 + Math.round((completed / totalChunks) * 70);
        }
      })(),
  );
  try {
    await Promise.all(workers);
    await completeKbUploadApi(uploadId);
  } catch (error) {
    // 失败保留会话（已传分片可复用）；主动放弃由用户通过重新选择文件覆盖
    throw error;
  }
}

// ------------------------------------------------------------------
// 列表
// ------------------------------------------------------------------

function fmtSize(bytes: number) {
  return bytes >= 1024 * 1024
    ? `${(bytes / 1024 / 1024).toFixed(1)} MB`
    : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

function statusTagType(status: KbDocItem['status']) {
  return status === 1 ? 'success' : status === 2 ? 'danger' : 'warning';
}

function statusText(status: KbDocItem['status']) {
  return status === 1
    ? $t('kb.statusDone')
    : status === 2
      ? $t('kb.statusFailed')
      : $t('kb.statusProcessing');
}

/** 处理阶段文案（与后端 KbPipelineExecutor.STAGE_* 对齐） */
function stageText(stage: null | string | undefined) {
  switch (stage) {
    case 'PARSING': {
      return $t('kb.stageParsing');
    }
    case 'CHUNKING': {
      return $t('kb.stageChunking');
    }
    case 'EMBEDDING': {
      return $t('kb.stageEmbedding');
    }
    default: {
      return '';
    }
  }
}

const gridOptions: VxeTableGridOptions<KbDocItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'fileName', title: $t('kb.fileName'), minWidth: 180, showOverflow: true },
    {
      field: 'sizeBytes',
      title: $t('kb.size'),
      width: 90,
      formatter: ({ cellValue }) => fmtSize(Number(cellValue ?? 0)),
    },
    {
      field: 'status',
      title: $t('kb.status'),
      width: 150,
      slots: { default: 'status' },
    },
    { field: 'chunkCount', title: $t('kb.chunkCount'), width: 90 },
    {
      field: 'uploaderName',
      title: $t('kb.uploader'),
      width: 110,
      showOverflow: true,
    },
    {
      field: 'createTime',
      title: $t('kb.createTime'),
      width: 165,
      formatter: ({ cellValue }) =>
        typeof cellValue === 'string'
          ? (cellValue.split('.')[0]?.replace('T', ' ') ?? cellValue)
          : (cellValue ?? ''),
    },
    { title: $t('kb.action'), width: 130, fixed: 'right', slots: { default: 'action' } },
  ],
  pagerConfig: { enabled: true },
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        const result = await getKbDocListApi({
          fileName: formValues?.fileName,
          pageNo: page.currentPage,
          pageSize: page.pageSize,
        });
        syncPolling(result.items);
        return result;
      },
    },
  },
  toolbarConfig: {
    custom: true,
    refresh: true,
    refreshOptions: { code: 'query' },
    search: true,
  },
};

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions,
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { placeholder: $t('kb.nameSearchPlaceholder') },
        fieldName: 'fileName',
        label: $t('kb.fileNameSearch'),
      },
    ],
  },
});

// ------------------------------------------------------------------
// 处理中轮询：列表存在处理中文档时每 3s 刷新，全部落定即停
// ------------------------------------------------------------------

const POLL_INTERVAL = 3000;

const pollTimer = ref<null | ReturnType<typeof setInterval>>(null);
/** 列表当前页是否有处理中文档（驱动轮询启停与按钮态） */
const hasProcessing = ref(false);

const uploadDisabled = computed(() => uploading.value);

function syncPolling(items: KbDocItem[]) {
  hasProcessing.value = items.some((item) => item.status === 0);
  if (hasProcessing.value && pollTimer.value === null) {
    pollTimer.value = setInterval(() => gridApi.query(), POLL_INTERVAL);
  } else if (!hasProcessing.value && pollTimer.value !== null) {
    clearInterval(pollTimer.value);
    pollTimer.value = null;
  }
}

onUnmounted(() => {
  if (pollTimer.value !== null) {
    clearInterval(pollTimer.value);
    pollTimer.value = null;
  }
});

// ------------------------------------------------------------------
// 行操作
// ------------------------------------------------------------------

async function remove(row: KbDocItem) {
  await ElMessageBox.confirm(
    $t('kb.deleteConfirm', { name: row.fileName }),
    $t('kb.deleteTitle'),
    { type: 'warning' },
  );
  await deleteKbDocApi(row.id);
  ElMessage.success($t('kb.deleteSuccess'));
  gridApi.reload();
}

async function retry(row: KbDocItem) {
  await ElMessageBox.confirm(
    $t('kb.retryConfirm', { name: row.fileName }),
    $t('kb.retryTitle'),
    { type: 'warning' },
  );
  await retryKbDocApi(row.id);
  ElMessage.success($t('kb.retryAccepted'));
  gridApi.query();
}
</script>

<template>
  <Page auto-content-height>
    <SearchModal />
    <Grid>
      <template #toolbar-actions>
        <ElUpload
          v-access:code="'Kb:Doc:Upload'"
          accept=".md,.txt,.markdown,.pdf,.docx"
          :disabled="uploadDisabled"
          :http-request="onDocUpload"
          :show-file-list="false"
        >
          <VbenButton variant="default" :loading="uploading">
            {{
              uploading
                ? `${$t('kb.uploading')} ${uploadPercent > 0 ? `${uploadPercent}%` : ''}`
                : $t('kb.upload')
            }}
          </VbenButton>
        </ElUpload>
        <VbenButton
          v-access:code="'Kb:Doc:List'"
          variant="default"
          class="ml-2"
          :disabled="uploadDisabled"
          @click="openSearch"
        >
          {{ $t('kb.searchTest') }}
        </VbenButton>
      </template>

      <template #status="{ row }">
        <div v-if="(row as KbDocItem).status === 0">
          <ElTag type="warning" size="small">{{ $t('kb.statusProcessing') }}</ElTag>
          <div class="mt-1 text-xs text-muted-foreground">
            {{ stageText((row as KbDocItem).stage) }}
          </div>
          <ElProgress
            :percentage="(row as KbDocItem).progress ?? 0"
            :stroke-width="6"
            :show-text="false"
          />
        </div>
        <ElTooltip
          v-else-if="(row as KbDocItem).status === 2 && (row as KbDocItem).errorMsg"
          :content="(row as KbDocItem).errorMsg ?? ''"
          placement="top"
        >
          <ElTag :type="statusTagType((row as KbDocItem).status)" size="small">
            {{ statusText((row as KbDocItem).status) }}
          </ElTag>
        </ElTooltip>
        <ElTag v-else :type="statusTagType((row as KbDocItem).status)" size="small">
          {{ statusText((row as KbDocItem).status) }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <VbenButton
          v-if="(row as KbDocItem).status === 2"
          v-access:code="'Kb:Doc:Upload'"
          variant="link"
          size="sm"
          @click="retry(row as KbDocItem)"
        >
          {{ $t('kb.retry') }}
        </VbenButton>
        <VbenButton
          v-access:code="'Kb:Doc:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          :disabled="(row as KbDocItem).status === 0"
          @click="remove(row as KbDocItem)"
        >
          {{ $t('kb.delete') }}
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
