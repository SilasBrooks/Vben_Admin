<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { OperLogItem } from '#/api/monitor/log';

import { Page, VbenButton } from '@vben/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { clearOperLogApi, deleteOperLogApi, getOperLogListApi } from '#/api/monitor/log';

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
    { field: 'operName', title: '操作人', width: 110 },
    { field: 'module', title: '模块', width: 110 },
    { field: 'description', title: '操作', minWidth: 120 },
    { field: 'requestMethod', title: '方式', width: 70 },
    { field: 'requestUrl', title: 'URL', minWidth: 180, showOverflow: true },
    {
      field: 'status',
      title: '结果',
      width: 80,
      formatter: ({ cellValue }) =>
        cellValue === 0 ? '成功' : '失败',
    },
    { field: 'ip', title: 'IP', width: 120 },
    { field: 'costMs', title: '耗时(ms)', width: 90 },
    { field: 'operTime', title: '操作时间', width: 170 },
    { title: '操作', width: 90, fixed: 'right', slots: { default: 'action' } },
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
        componentProps: { placeholder: '请输入操作人' },
        fieldName: 'operName',
        label: '操作人',
      },
      {
        component: 'Select',
        componentProps: {
          placeholder: '请选择结果',
          clearable: true,
          options: [
            { label: '成功', value: 0 },
            { label: '失败', value: 1 },
          ],
        },
        fieldName: 'status',
        label: '结果',
      },
      {
        component: 'DatePicker',
        componentProps: { type: 'daterange', valueFormat: 'YYYY-MM-DD' },
        fieldName: 'timeRange',
        label: '时间',
      },
    ],
  },
});

async function remove(row: OperLogItem) {
  await ElMessageBox.confirm(`确认删除该条操作日志？`, '提示', {
    type: 'warning',
  });
  await deleteOperLogApi(row.id);
  ElMessage.success('删除成功');
  gridApi.reload();
}

async function clearAll() {
  await ElMessageBox.confirm('确认清空全部操作日志？该操作不可恢复！', '警告', {
    type: 'warning',
  });
  await clearOperLogApi();
  ElMessage.success('已清空');
  gridApi.reload();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-actions>
        <VbenButton
          v-access:code="'Monitor:OperLog:Delete'"
          variant="default"
          class="text-destructive"
          @click="clearAll"
        >
          清空日志
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
          删除
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
