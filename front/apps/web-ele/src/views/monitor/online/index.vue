<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { OnlineUserItem } from '#/api/monitor/online';

import { Page, VbenButton } from '@vben/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getOnlineUserListApi, kickUserApi } from '#/api/monitor/online';

const gridOptions: VxeTableGridOptions<OnlineUserItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'username', title: '用户名', minWidth: 140 },
    { field: 'nickname', title: '昵称', minWidth: 120 },
    {
      field: 'loginTime',
      title: '登录时间',
      width: 170,
      formatter: ({ cellValue }) =>
        typeof cellValue === 'string' ? cellValue.replace('T', ' ') : (cellValue ?? ''),
    },
    { field: 'ip', title: '登录 IP', width: 140 },
    { title: '操作', width: 100, fixed: 'right', slots: { default: 'action' } },
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
        componentProps: { placeholder: '请输入用户名' },
        fieldName: 'username',
        label: '用户名',
      },
    ],
  },
});

async function kick(row: OnlineUserItem) {
  await ElMessageBox.confirm(
    `确认将用户「${row.username}」强制下线？其当前登录将立即失效。`,
    '强制下线',
    { type: 'warning' },
  );
  await kickUserApi(row.userId);
  ElMessage.success('已强制下线');
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
          强制下线
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
