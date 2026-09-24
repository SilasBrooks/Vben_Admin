<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { Page, VbenButton, useVbenModal } from '@vben/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import { deleteUserApi, getUserListApi } from '#/api/system/user';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { $t } from '#/locales';

import UserForm from './user-form.vue';

type VxeGridRow = {
  id: number;
  username: string;
  nickname?: string;
  homePath?: string;
  deptName?: string;
  status: number;
  createTime?: string;
};

// 用户表单弹窗（新增/编辑共用）
const [UserFormModal, userFormApi] = useVbenModal({
  connectedComponent: UserForm,
});

// 列定义 + proxyConfig（接后端分页）
const gridOptions: VxeTableGridOptions<VxeGridRow> = {
  id: 'table.system.user',
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'id', title: 'ID', visible: false },
    { field: 'username', title: $t('system.user.username'), minWidth: 140 },
    { field: 'nickname', title: $t('system.user.nickname'), minWidth: 140 },
    { field: 'deptName', title: $t('system.user.dept'), minWidth: 120 },
    { field: 'homePath', title: $t('system.user.home'), minWidth: 140 },
    {
      field: 'status',
      title: $t('system.common.status'),
      width: 90,
      formatter: ({ cellValue }) =>
        cellValue === 0
          ? $t('system.common.enabled')
          : $t('system.common.disabled'),
    },
    {
      field: '__actions',
      title: $t('system.common.actions'),
      width: 200,
      fixed: 'right',
      slots: { default: 'action' },
    },
  ],
  pagerConfig: { enabled: true },
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        return await getUserListApi({
          pageNo: page.currentPage,
          pageSize: page.pageSize,
          username: formValues?.username,
          status: formValues?.status,
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
        componentProps: { placeholder: $t('system.user.enterUsername') },
        fieldName: 'username',
        label: $t('system.user.username'),
      },
      {
        component: 'Select',
        componentProps: {
          placeholder: $t('system.common.selectStatus'),
          clearable: true,
          options: [
            { label: $t('system.common.enabled'), value: 0 },
            { label: $t('system.common.disabled'), value: 1 },
          ],
        },
        fieldName: 'status',
        label: $t('system.common.status'),
      },
    ],
  },
});

function openCreate() {
  userFormApi.setData({}).open();
}

function openEdit(row: VxeGridRow) {
  userFormApi.setData({ id: row.id }).open();
}

async function remove(row: VxeGridRow) {
  await ElMessageBox.confirm(
    $t('system.user.deleteConfirm', { name: row.username }),
    $t('system.common.notice'),
    {
      type: 'warning',
    },
  );
  await deleteUserApi(row.id);
  ElMessage.success($t('system.common.deleteSuccess'));
  gridApi.reload();
}

function onFormSaved() {
  gridApi.reload();
}
</script>

<template>
  <Page auto-content-height>
    <UserFormModal @saved="onFormSaved" />
    <Grid>
      <template #toolbar-actions>
        <VbenButton
          v-access:code="'System:User:Add'"
          variant="default"
          @click="openCreate"
        >
          {{ $t('system.user.add') }}
        </VbenButton>
      </template>
      <template #action="{ row }">
        <VbenButton
          v-access:code="'System:User:Edit'"
          variant="link"
          size="sm"
          @click="openEdit(row)"
        >
          {{ $t('system.common.edit') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:User:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          @click="remove(row as VxeGridRow)"
        >
          {{ $t('system.common.delete') }}
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
