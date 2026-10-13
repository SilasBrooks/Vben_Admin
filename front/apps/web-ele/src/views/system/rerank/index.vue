<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { RerankItem } from '#/api/system/rerank';

import { Page, VbenButton, useVbenModal } from '@vben/common-ui';

import { ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  activateRerankApi,
  deleteRerankApi,
  getRerankListApi,
  testRerankApi,
} from '#/api/system/rerank';
import { $t } from '#/locales';

import RerankForm from './rerank-form.vue';

// ------------------------------------------------------------------
// 新增/编辑弹窗（项目封装 useVbenModal，新增/编辑共用）
// ------------------------------------------------------------------

const [RerankFormModal, rerankFormApi] = useVbenModal({
  connectedComponent: RerankForm,
});

function openAdd() {
  rerankFormApi.setData({}).open();
}

function openEdit(row: RerankItem) {
  rerankFormApi.setData({ ...row }).open();
}

function onFormSaved() {
  gridApi.reload();
}

// ------------------------------------------------------------------
// 列表
// ------------------------------------------------------------------

const gridOptions: VxeTableGridOptions<RerankItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'name', title: $t('rerank.name'), minWidth: 140, showOverflow: true },
    { field: 'model', title: $t('rerank.model'), minWidth: 180, showOverflow: true },
    {
      field: 'baseUrl',
      title: $t('rerank.baseUrl'),
      minWidth: 220,
      showOverflow: true,
    },
    { field: 'apiKey', title: $t('rerank.apiKey'), minWidth: 140, showOverflow: true },
    {
      field: 'enabled',
      title: $t('rerank.status'),
      width: 90,
      slots: { default: 'status' },
    },
    {
      field: 'isActive',
      title: $t('rerank.active'),
      width: 90,
      slots: { default: 'active' },
    },
    {
      field: 'createTime',
      title: $t('rerank.createTime'),
      width: 165,
      formatter: ({ cellValue }) =>
        typeof cellValue === 'string'
          ? (cellValue.split('.')[0]?.replace('T', ' ') ?? cellValue)
          : (cellValue ?? ''),
    },
    { title: $t('rerank.action'), width: 220, fixed: 'right', slots: { default: 'action' } },
  ],
  pagerConfig: { enabled: true },
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        return await getRerankListApi({
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
        componentProps: { placeholder: $t('rerank.nameSearchPlaceholder') },
        fieldName: 'name',
        label: $t('rerank.nameSearch'),
      },
    ],
  },
});

// ------------------------------------------------------------------
// 行操作
// ------------------------------------------------------------------

async function testConnect(row: RerankItem) {
  const loading = ElMessage({
    message: $t('rerank.testing'),
    type: 'info',
    duration: 0,
  });
  try {
    const result = await testRerankApi(row.id, row.timeoutSeconds);
    loading.close();
    ElMessage.success(
      $t('rerank.testSuccess', {
        model: row.model,
        score: result.topScore,
        ms: result.elapsedMs,
      }),
    );
  } catch (error) {
    loading.close();
    const reason = error instanceof Error ? error.message : String(error);
    ElMessage.error($t('rerank.testFailed', { reason }));
  }
}

async function activate(row: RerankItem) {
  await ElMessageBox.confirm(
    $t('rerank.activateConfirm', { name: row.name }),
    $t('rerank.activateTitle'),
    { type: 'warning' },
  );
  await activateRerankApi(row.id);
  ElMessage.success($t('rerank.activateSuccess'));
  gridApi.reload();
}

async function remove(row: RerankItem) {
  await ElMessageBox.confirm(
    $t('rerank.deleteConfirm', { name: row.name }),
    $t('rerank.deleteTitle'),
    { type: 'warning' },
  );
  await deleteRerankApi(row.id);
  ElMessage.success($t('rerank.deleteSuccess'));
  gridApi.reload();
}
</script>

<template>
  <Page auto-content-height>
    <RerankFormModal @saved="onFormSaved" />
    <Grid>
      <template #toolbar-actions>
        <VbenButton
          v-access:code="'System:Rerank:Add'"
          variant="default"
          @click="openAdd"
        >
          {{ $t('rerank.add') }}
        </VbenButton>
      </template>

      <template #status="{ row }">
        <ElTag :type="(row as RerankItem).enabled === 1 ? 'success' : 'info'" size="small">
          {{
            (row as RerankItem).enabled === 1
              ? $t('rerank.enabledOn')
              : $t('rerank.enabledOff')
          }}
        </ElTag>
      </template>

      <template #active="{ row }">
        <ElTag
          :type="(row as RerankItem).isActive === 1 ? 'warning' : 'info'"
          size="small"
          :effect="(row as RerankItem).isActive === 1 ? 'dark' : 'plain'"
        >
          {{
            (row as RerankItem).isActive === 1 ? $t('rerank.active') : $t('rerank.inactive')
          }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <VbenButton
          v-access:code="'System:Rerank:Edit'"
          variant="link"
          size="sm"
          @click="openEdit(row as RerankItem)"
        >
          {{ $t('rerank.edit') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Rerank:Edit'"
          variant="link"
          size="sm"
          @click="testConnect(row as RerankItem)"
        >
          {{ $t('rerank.test') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Rerank:Activate'"
          variant="link"
          size="sm"
          :disabled="(row as RerankItem).isActive === 1"
          @click="activate(row as RerankItem)"
        >
          {{ $t('rerank.activate') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Rerank:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          :disabled="(row as RerankItem).isActive === 1"
          @click="remove(row as RerankItem)"
        >
          {{ $t('rerank.delete') }}
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
