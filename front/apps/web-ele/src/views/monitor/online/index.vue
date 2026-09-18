<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { OnlineUserItem } from '#/api/monitor/online';

import { Page, VbenButton } from '@vben/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getOnlineUserListApi, kickUserApi } from '#/api/monitor/online';

import { $t } from '#/locales';

const gridOptions: VxeTableGridOptions<OnlineUserItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'username', title: $t('monitor.common.username'), minWidth: 140 },
    { field: 'nickname', title: $t('monitor.online.nickname'), minWidth: 120 },
    {
      field: 'loginTime',
      title: $t('monitor.common.loginTime'),
      width: 170,
      formatter: ({ cellValue }) =>
        typeof cellValue === 'string' ? cellValue.replace('T', ' ') : (cellValue ?? ''),
    },
    { field: 'ip', title: $t('monitor.online.loginIp'), width: 140 },
    { title: $t('monitor.common.action'), width: 100, fixed: 'right', slots: { default: 'action' } },
  ],
  pagerConfig: { enabled: true },
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        return await getOnlineUserListApi({
          pageNo: page.currentPage,
          pageSize: page.pageSize,
          username: formValues?.username,
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
    ],
  },
});

async function kick(row: OnlineUserItem) {
  await ElMessageBox.confirm(
    $t('monitor.online.kickConfirm', { name: row.username }),
    $t('monitor.online.kick'),
    { type: 'warning' },
  );
  await kickUserApi(row.userId);
  ElMessage.success($t('monitor.online.kickSuccess'));
  gridApi.reload();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #action="{ row }">
        <VbenButton
          v-access:code="'Monitor:Online:Kick'"
          variant="link"
          size="sm"
          class="text-destructive"
          @click="kick(row as OnlineUserItem)"
        >
          {{ $t('monitor.online.kick') }}
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
