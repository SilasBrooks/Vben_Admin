<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { FileItem } from '#/api/system/file';

import { ref } from 'vue';

import { Page, VbenButton } from '@vben/common-ui';

import { ElDialog, ElMessage, ElMessageBox } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteFileApi,
  downloadFileApi,
  getFileListApi,
  uploadFileApi,
} from '#/api/system/file';

/** 上传白名单（与后端 SysFileService 一致） */
const ACCEPT =
  '.jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip';

function formatSize(bytes: number): string {
  if (bytes == null) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

const gridOptions: VxeTableGridOptions<FileItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'originalName', title: '原始文件名', minWidth: 200, showOverflow: true },
    {
      field: 'size',
      title: '大小',
      width: 100,
      formatter: ({ cellValue }) => formatSize(cellValue),
    },
    { field: 'contentType', title: '类型', minWidth: 140 },
    { field: 'uploaderName', title: '上传人', width: 120 },
    {
      field: 'createTime',
      title: '上传时间',
      width: 170,
      formatter: ({ cellValue }) =>
        typeof cellValue === 'string' ? cellValue.replace('T', ' ') : (cellValue ?? ''),
    },
    { title: '操作', width: 140, fixed: 'right', slots: { default: 'action' } },
  ],
  pagerConfig: { enabled: true },
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        return await getFileListApi({
          pageNo: page.currentPage,
          pageSize: page.pageSize,
          originalName: formValues?.originalName,
        });
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
        componentProps: { placeholder: '请输入原始文件名' },
        fieldName: 'originalName',
        label: '文件名',
      },
    ],
  },
});

const fileInputRef = ref<HTMLInputElement>();
const previewVisible = ref(false);
const previewUrl = ref('');
const previewName = ref('');

function chooseFile() {
  fileInputRef.value?.click();
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  try {
    await uploadFileApi(file);
    ElMessage.success('上传成功');
    gridApi.reload();
  } finally {
    // 清空 value 保证同名文件可重复选择
    input.value = '';
  }
}

async function preview(row: FileItem) {
  const blob = await downloadFileApi(row.id);
  const url = URL.createObjectURL(blob);
  if (row.contentType?.startsWith('image/')) {
    previewUrl.value = url;
    previewName.value = row.originalName;
    previewVisible.value = true;
  } else {
    // 非图片：以原始名触发浏览器下载
    const a = document.createElement('a');
    a.href = url;
    a.download = row.originalName;
    a.click();
    URL.revokeObjectURL(url);
  }
}

function onPreviewClose() {
  URL.revokeObjectURL(previewUrl.value);
  previewUrl.value = '';
}

async function remove(row: FileItem) {
  await ElMessageBox.confirm(
    `确认删除文件「${row.originalName}」？记录与物理文件将一并移除，不可恢复。`,
    '删除文件',
    { type: 'warning' },
  );
  await deleteFileApi(row.id);
  ElMessage.success('删除成功');
  gridApi.reload();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-actions>
        <VbenButton
          v-access:code="'System:File:Upload'"
          variant="default"
          @click="chooseFile"
        >
          上传文件
        </VbenButton>
        <input
          ref="fileInputRef"
          type="file"
          :accept="ACCEPT"
          class="hidden"
          @change="onFileChange"
        />
      </template>
      <template #action="{ row }">
        <VbenButton
          variant="link"
          size="sm"
          @click="preview(row as FileItem)"
        >
          预览
        </VbenButton>
        <VbenButton
          v-access:code="'System:File:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          @click="remove(row as FileItem)"
        >
          删除
        </VbenButton>
      </template>
    </Grid>
    <ElDialog
      v-model="previewVisible"
      :title="previewName"
      width="640px"
      destroy-on-close
      @closed="onPreviewClose"
    >
      <img
        v-if="previewUrl"
        :src="previewUrl"
        :alt="previewName"
        class="mx-auto max-h-[480px] max-w-full"
      />
    </ElDialog>
  </Page>
</template>
