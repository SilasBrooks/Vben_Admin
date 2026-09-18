<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { LoginLogItem } from '#/api/monitor/log';

import { Page, VbenButton } from '@vben/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { clearLoginLogApi, deleteLoginLogApi, getLoginLogListApi } from '#/api/monitor/log';

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

const gridOptions: VxeTableGridOptions<LoginLogItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'username', title: $t('monitor.common.username'), minWidth: 120 },
    {
      field: 'status',
      title: $t('monitor.common.result'),
      width: 80,
      formatter: ({ cellValue }) =>
        cellValue === 0
          ? $t('monitor.common.success')
          : $t('monitor.common.fail'),
    },
    { field: 'message', title: $t('monitor.loginLog.message'), minWidth: 140 },
    { field: 'ip', title: $t('monitor.common.ip'), width: 130 },
    { field: 'userAgent', title: $t('monitor.loginLog.browser'), minWidth: 200, showOverflow: true },
    { field: 'loginTime', title: $t('monitor.common.loginTime'), width: 170 },
    { title: $t('monitor.common.action'), width: 90, fixed: 'right', slots: { default: 'action' } },
  ],
  pagerConfig: { enabled: true },
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        return await getLoginLogListApi({
          pageNo: page.currentPage,
          pageSize: page.pageSize,
          username: formValues?.username,
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
        componentProps: { placeholder: $t('monitor.common.usernamePlaceholder') },
        fieldName: 'username',
        label: $t('monitor.common.username'),
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

async function remove(row: LoginLogItem) {
  await ElMessageBox.confirm(
    $t('monitor.loginLog.deleteConfirm'),
    $t('monitor.common.confirmTitle'),
    { type: 'warning' },
  );
  await deleteLoginLogApi(row.id);
  ElMessage.success($t('monitor.common.deleteSuccess'));
  gridApi.reload();
}

async function clearAll() {
  await ElMessageBox.confirm(
    $t('monitor.loginLog.clearConfirm'),
    $t('monitor.common.warningTitle'),
    { type: 'warning' },
  );
  await clearLoginLogApi();
  ElMessage.success($t('monitor.common.cleared'));
  gridApi.reload();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-actions>
        <VbenButton
          v-access:code="'Monitor:LoginLog:Delete'"
          variant="default"
          class="text-destructive"
          @click="clearAll"
        >
          {{ $t('monitor.common.clearLogs') }}
        </VbenButton>
      </template>
      <template #action="{ row }">
        <VbenButton
          v-access:code="'Monitor:LoginLog:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          @click="remove(row as LoginLogItem)"
        >
          {{ $t('monitor.common.delete') }}
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
