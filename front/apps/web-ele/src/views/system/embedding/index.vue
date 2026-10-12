<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { EmbItem } from '#/api/system/embedding';

import { Page, VbenButton, useVbenModal } from '@vben/common-ui';

import { ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  activateEmbApi,
  deleteEmbApi,
  getEmbListApi,
  testEmbApi,
} from '#/api/system/embedding';
import { $t } from '#/locales';

import EmbForm from './embedding-form.vue';

// ------------------------------------------------------------------
// 新增/编辑弹窗（项目封装 useVbenModal，新增/编辑共用）
// ------------------------------------------------------------------

const [EmbFormModal, embFormApi] = useVbenModal({
  connectedComponent: EmbForm,
});

function openAdd() {
  embFormApi.setData({}).open();
}

function openEdit(row: EmbItem) {
  embFormApi.setData({ ...row }).open();
}

function onFormSaved() {
  gridApi.reload();
}

// ------------------------------------------------------------------
// 列表
// ------------------------------------------------------------------

const gridOptions: VxeTableGridOptions<EmbItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'name', title: $t('embedding.name'), minWidth: 140, showOverflow: true },
    { field: 'model', title: $t('embedding.model'), minWidth: 130, showOverflow: true },
    {
      field: 'baseUrl',
      title: $t('embedding.baseUrl'),
      minWidth: 220,
      showOverflow: true,
    },
    { field: 'apiKey', title: $t('embedding.apiKey'), minWidth: 140, showOverflow: true },
    {
      field: 'dimension',
      title: $t('embedding.dimension'),
      width: 90,
      slots: { default: 'dimension' },
    },
    {
      field: 'enabled',
      title: $t('embedding.status'),
      width: 90,
      slots: { default: 'status' },
    },
    {
      field: 'isActive',
      title: $t('embedding.active'),
      width: 90,
      slots: { default: 'active' },
    },
    {
      field: 'createTime',
      title: $t('embedding.createTime'),
      width: 165,
      formatter: ({ cellValue }) =>
        typeof cellValue === 'string'
          ? (cellValue.split('.')[0]?.replace('T', ' ') ?? cellValue)
          : (cellValue ?? ''),
    },
    { title: $t('embedding.action'), width: 220, fixed: 'right', slots: { default: 'action' } },
  ],
  pagerConfig: { enabled: true },
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        return await getEmbListApi({
          name: formValues?.name,
          pageNo: page.currentPage,
          pageSize: page.pageSize,
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
        componentProps: { placeholder: $t('embedding.nameSearchPlaceholder') },
        fieldName: 'name',
        label: $t('embedding.nameSearch'),
      },
    ],
  },
});

// ------------------------------------------------------------------
// 行操作
// ------------------------------------------------------------------

async function testConnect(row: EmbItem) {
  const loading = ElMessage({
    message: $t('embedding.testing'),
    type: 'info',
    duration: 0,
  });
  try {
    const result = await testEmbApi(row.id, row.timeoutSeconds);
    loading.close();
    ElMessage.success(
      $t('embedding.testSuccess', {
        model: row.model,
        dimension: result.dimension,
        ms: result.elapsedMs,
      }),
    );
  } catch (error) {
    loading.close();
    const reason = error instanceof Error ? error.message : String(error);
    ElMessage.error($t('embedding.testFailed', { reason }));
  }
}

async function activate(row: EmbItem) {
  await ElMessageBox.confirm(
    $t('embedding.activateConfirm', { name: row.name }),
    $t('embedding.activateTitle'),
    { type: 'warning' },
  );
  await activateEmbApi(row.id);
  ElMessage.success($t('embedding.activateSuccess'));
  gridApi.reload();
}

async function remove(row: EmbItem) {
  await ElMessageBox.confirm(
    $t('embedding.deleteConfirm', { name: row.name }),
    $t('embedding.deleteTitle'),
    { type: 'warning' },
  );
  await deleteEmbApi(row.id);
  ElMessage.success($t('embedding.deleteSuccess'));
  gridApi.reload();
}
</script>

<template>
  <Page auto-content-height>
    <EmbFormModal @saved="onFormSaved" />
    <Grid>
      <template #toolbar-actions>
        <VbenButton
          v-access:code="'System:Emb:Add'"
          variant="default"
          @click="openAdd"
        >
          {{ $t('embedding.add') }}
        </VbenButton>
      </template>

      <template #dimension="{ row }">
        <span v-if="(row as EmbItem).dimension">{{ (row as EmbItem).dimension }}</span>
        <span v-else class="text-muted-foreground">{{ $t('embedding.dimensionUnknown') }}</span>
      </template>

      <template #status="{ row }">
        <ElTag :type="(row as EmbItem).enabled === 1 ? 'success' : 'info'" size="small">
          {{
            (row as EmbItem).enabled === 1
              ? $t('embedding.enabledOn')
              : $t('embedding.enabledOff')
          }}
        </ElTag>
      </template>

      <template #active="{ row }">
        <ElTag
          :type="(row as EmbItem).isActive === 1 ? 'warning' : 'info'"
          size="small"
          :effect="(row as EmbItem).isActive === 1 ? 'dark' : 'plain'"
        >
          {{
            (row as EmbItem).isActive === 1 ? $t('embedding.active') : $t('embedding.inactive')
          }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <VbenButton
          v-access:code="'System:Emb:Edit'"
          variant="link"
          size="sm"
          @click="openEdit(row as EmbItem)"
        >
          {{ $t('embedding.edit') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Emb:Edit'"
          variant="link"
          size="sm"
          @click="testConnect(row as EmbItem)"
        >
          {{ $t('embedding.test') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Emb:Activate'"
          variant="link"
          size="sm"
          :disabled="(row as EmbItem).isActive === 1"
          @click="activate(row as EmbItem)"
        >
          {{ $t('embedding.activate') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Emb:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          :disabled="(row as EmbItem).isActive === 1"
          @click="remove(row as EmbItem)"
        >
          {{ $t('embedding.delete') }}
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
