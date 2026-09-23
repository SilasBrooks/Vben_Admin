<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { OperLogItem } from '#/api/monitor/log';

import { Page, VbenButton } from '@vben/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { clearOperLogApi, deleteOperLogApi, getOperLogListApi } from '#/api/monitor/log';

import { $t } from '#/locales';

// 时间范围表单值拆为 beginTime/endTime
function splitRange(
  formValues?: Record<string, any>,
): { beginTime?: string; endTime?: string } {
  const range = formValues?.timeRange as [string, string] | undefined;
  return range && range.length === 2
    ? { beginTime: range[0], endTime: range[1] }
    : {};
}

const gridOptions: VxeTableGridOptions<OperLogItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'operName', title: $t('monitor.operLog.operName'), width: 110 },
    { field: 'module', title: $t('monitor.operLog.module'), width: 110 },
    { field: 'description', title: $t('monitor.operLog.operation'), minWidth: 120 },
    { field: 'requestMethod', title: $t('monitor.operLog.method'), width: 70 },
    { field: 'requestUrl', title: $t('monitor.operLog.url'), minWidth: 180, showOverflow: true },
    {
      field: 'status',
      title: $t('monitor.common.result'),
      width: 80,
      formatter: ({ cellValue }) =>
        cellValue === 0
          ? $t('monitor.common.success')
          : $t('monitor.common.fail'),
    },
    { field: 'ip', title: $t('monitor.common.ip'), width: 120 },
    { field: 'costMs', title: $t('monitor.operLog.cost'), width: 90 },
    { field: 'operTime', title: $t('monitor.operLog.operTime'), width: 170 },
    { title: $t('monitor.common.action'), width: 90, fixed: 'right', slots: { default: 'action' } },
  ],
  pagerConfig: { enabled: true },
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        return await getOperLogListApi({
          pageNo: page.currentPage,
          pageSize: page.pageSize,
          operName: formValues?.operName,
          status: formValues?.status,
          ...splitRange(formValues),
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
        componentProps: { placeholder: $t('monitor.operLog.operNamePlaceholder') },
        fieldName: 'operName',
        label: $t('monitor.operLog.operName'),
      },
      {
        component: 'Select',
        componentProps: {
          placeholder: $t('monitor.common.resultPlaceholder'),
          clearable: true,
          options: [
            { label: $t('monitor.common.success'), value: 0 },
            { label: $t('monitor.common.fail'), value: 1 },
          ],
        },
        fieldName: 'status',
        label: $t('monitor.common.result'),
      },
      {
        component: 'DatePicker',
        componentProps: { type: 'daterange', valueFormat: 'YYYY-MM-DD' },
        fieldName: 'timeRange',
        label: $t('monitor.common.time'),
      },
    ],
  },
});

async function remove(row: OperLogItem) {
  await ElMessageBox.confirm(
    $t('monitor.operLog.deleteConfirm'),
    $t('monitor.common.confirmTitle'),
    { type: 'warning' },
  );
  await deleteOperLogApi(row.id);
  ElMessage.success($t('monitor.common.deleteSuccess'));
  gridApi.reload();
}

async function clearAll() {
  await ElMessageBox.confirm(
    $t('monitor.operLog.clearConfirm'),
    $t('monitor.common.warningTitle'),
    { type: 'warning' },
  );
  await clearOperLogApi();
  ElMessage.success($t('monitor.common.cleared'));
  gridApi.reload();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-actions>
        <VbenButton
          v-access:code="'Monitor:OperLog:Delete'"
          variant="destructive"
          @click="clearAll"
        >
          {{ $t('monitor.common.clearLogs') }}
        </VbenButton>
      </template>
      <template #action="{ row }">
        <VbenButton
          v-access:code="'Monitor:OperLog:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          @click="remove(row as OperLogItem)"
        >
          {{ $t('monitor.common.delete') }}
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
