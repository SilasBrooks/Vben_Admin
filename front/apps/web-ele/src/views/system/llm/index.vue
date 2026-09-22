<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { LlmItem } from '#/api/system/llm';

import { Page, VbenButton, useVbenModal } from '@vben/common-ui';

import { ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  activateLlmApi,
  deleteLlmApi,
  getLlmListApi,
  testLlmApi,
} from '#/api/system/llm';
import { $t } from '#/locales';

import LlmForm from './llm-form.vue';

// ------------------------------------------------------------------
// 新增/编辑弹窗（项目封装 useVbenModal，新增/编辑共用）
// ------------------------------------------------------------------

const [LlmFormModal, llmFormApi] = useVbenModal({
  connectedComponent: LlmForm,
});

function openAdd() {
  llmFormApi.setData({}).open();
}

function openEdit(row: LlmItem) {
  llmFormApi.setData({ ...row }).open();
}

function onFormSaved() {
  gridApi.reload();
}

// ------------------------------------------------------------------
// 列表
// ------------------------------------------------------------------

const gridOptions: VxeTableGridOptions<LlmItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'name', title: $t('llm.name'), minWidth: 140, showOverflow: true },
    { field: 'model', title: $t('llm.model'), minWidth: 130, showOverflow: true },
    {
      field: 'baseUrl',
      title: $t('llm.baseUrl'),
      minWidth: 220,
      showOverflow: true,
    },
    { field: 'apiKey', title: $t('llm.apiKey'), minWidth: 140, showOverflow: true },
    {
      field: 'enabled',
      title: $t('llm.status'),
      width: 90,
      slots: { default: 'status' },
    },
    {
      field: 'isActive',
      title: $t('llm.active'),
      width: 90,
      slots: { default: 'active' },
    },
    {
      field: 'createTime',
      title: $t('llm.createTime'),
      width: 165,
      formatter: ({ cellValue }) =>
        typeof cellValue === 'string' ? cellValue.replace('T', ' ') : (cellValue ?? ''),
    },
    { title: $t('llm.action'), width: 220, fixed: 'right', slots: { default: 'action' } },
  ],
  pagerConfig: { enabled: true },
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        return await getLlmListApi({
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
        componentProps: { placeholder: $t('llm.nameSearchPlaceholder') },
        fieldName: 'name',
        label: $t('llm.nameSearch'),
      },
    ],
  },
});

// ------------------------------------------------------------------
// 行操作
// ------------------------------------------------------------------

async function testConnect(row: LlmItem) {
  const loading = ElMessage({
    message: $t('llm.testing'),
    type: 'info',
    duration: 0,
  });
  try {
    const result = await testLlmApi(row.id, row.timeoutSeconds);
    loading.close();
    ElMessage.success(
      $t('llm.testSuccess', { model: result.model, ms: result.elapsedMs }),
    );
  } catch (error) {
    loading.close();
    const reason = error instanceof Error ? error.message : String(error);
    ElMessage.error($t('llm.testFailed', { reason }));
  }
}

async function activate(row: LlmItem) {
  await ElMessageBox.confirm(
    $t('llm.activateConfirm', { name: row.name }),
    $t('llm.activateTitle'),
    { type: 'warning' },
  );
  await activateLlmApi(row.id);
  ElMessage.success($t('llm.activateSuccess'));
  gridApi.reload();
}

async function remove(row: LlmItem) {
  if (row.isActive === 1) {
    ElMessage.warning($t('llm.deleteConfirm', { name: row.name }));
    return;
  }
  await ElMessageBox.confirm(
    $t('llm.deleteConfirm', { name: row.name }),
    $t('llm.deleteTitle'),
    { type: 'warning' },
  );
  await deleteLlmApi(row.id);
  ElMessage.success($t('llm.deleteSuccess'));
  gridApi.reload();
}
</script>

<template>
  <Page auto-content-height>
    <LlmFormModal @saved="onFormSaved" />
    <Grid>
      <template #toolbar-actions>
        <VbenButton
          v-access:code="'System:Llm:Add'"
          variant="default"
          @click="openAdd"
        >
          {{ $t('llm.add') }}
        </VbenButton>
      </template>

      <template #status="{ row }">
        <ElTag :type="(row as LlmItem).enabled === 1 ? 'success' : 'info'" size="small">
          {{
            (row as LlmItem).enabled === 1
              ? $t('llm.enabledOn')
              : $t('llm.enabledOff')
          }}
        </ElTag>
      </template>

      <template #active="{ row }">
        <ElTag
          :type="(row as LlmItem).isActive === 1 ? 'warning' : 'info'"
          size="small"
          :effect="(row as LlmItem).isActive === 1 ? 'dark' : 'plain'"
        >
          {{
            (row as LlmItem).isActive === 1 ? $t('llm.active') : $t('llm.inactive')
          }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <VbenButton
          v-access:code="'System:Llm:Edit'"
          variant="link"
          size="sm"
          @click="openEdit(row as LlmItem)"
        >
          {{ $t('llm.edit') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Llm:Edit'"
          variant="link"
          size="sm"
          @click="testConnect(row as LlmItem)"
        >
          {{ $t('llm.test') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Llm:Activate'"
          variant="link"
          size="sm"
          :disabled="(row as LlmItem).isActive === 1"
          @click="activate(row as LlmItem)"
        >
          {{ $t('llm.activate') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Llm:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          :disabled="(row as LlmItem).isActive === 1"
          @click="remove(row as LlmItem)"
        >
          {{ $t('llm.delete') }}
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
