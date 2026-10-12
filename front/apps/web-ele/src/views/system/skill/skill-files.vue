<script lang="ts" setup>
import type { SkillFileItem } from '#/api/system/skill';

import { ref } from 'vue';

import { VbenButton, useVbenModal } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import {
  downloadSkillFileApi,
  downloadSkillPackageApi,
  getSkillFilesApi,
} from '#/api/system/skill';
import { $t } from '#/locales';

interface FilesData {
  id: number;
  name: string;
  /** 原包 zip 文件名（整包下载时的保存名，缺省兜底） */
  packageFileName?: null | string;
}

// 只读弹窗：包内文件清单 + 单文件下载 + 整包下载（无确认/提交动作）
const [Modal, modalApi] = useVbenModal({
  footer: false,
  onOpenChange(isOpen: boolean) {
    if (isOpen) {
      init();
    }
  },
});

const files = ref<SkillFileItem[]>([]);

async function init() {
  const data = modalApi.getData<FilesData>();
  modalApi.setState({
    title: $t('skill.filesTitle', { name: data?.name ?? '' }),
  });
  const res = await getSkillFilesApi(data?.id ?? 0);
  files.value = res.items;
}

function formatSize(size: number): string {
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(2)} MB`;
}

function fileBaseName(path: string): string {
  const index = path.lastIndexOf('/');
  return index >= 0 ? path.slice(index + 1) : path;
}

function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(url);
}

async function downloadFile(item: SkillFileItem) {
  const data = modalApi.getData<FilesData>();
  const blob = await downloadSkillFileApi(data?.id ?? 0, item.path);
  const fileName = fileBaseName(item.path);
  saveBlob(blob, fileName);
  ElMessage.success($t('skill.downloadSuccess', { name: fileName }));
}

async function downloadPackage() {
  const data = modalApi.getData<FilesData>();
  const blob = await downloadSkillPackageApi(data?.id ?? 0);
  const fileName = data?.packageFileName || 'skill-package.zip';
  saveBlob(blob, fileName);
  ElMessage.success($t('skill.downloadSuccess', { name: fileName }));
}
</script>

<template>
  <Modal class="w-[720px]">
    <div
      v-if="files.length === 0"
      class="text-muted-foreground py-6 text-center text-sm"
    >
      {{ $t('skill.filesEmpty') }}
    </div>
    <div v-else class="flex flex-col gap-2">
      <div
        v-for="item in files"
        :key="item.path"
        class="bg-accent flex items-center justify-between gap-3 rounded px-3 py-2"
      >
        <div class="min-w-0 flex-1">
          <div class="truncate text-sm">{{ item.path }}</div>
          <div class="text-muted-foreground text-xs">{{ formatSize(item.size) }}</div>
        </div>
        <VbenButton size="sm" variant="link" @click="downloadFile(item)">
          {{ $t('skill.fileDownload') }}
        </VbenButton>
      </div>
    </div>

    <div class="text-muted-foreground mt-3 text-xs leading-5">
      {{ $t('skill.filesEditHint') }}
    </div>

    <div class="mt-2 flex justify-end">
      <VbenButton size="sm" variant="default" @click="downloadPackage">
        {{ $t('skill.downloadPackage') }}
      </VbenButton>
    </div>
  </Modal>
</template>
