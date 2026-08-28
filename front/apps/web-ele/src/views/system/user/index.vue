<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { Page, VbenButton, useVbenModal } from '@vben/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import { deleteUserApi, getUserListApi } from '#/api/system/user';
import { useVbenVxeGrid } from '#/adapter/vxe-table';

import UserForm from './user-form.vue';

type VxeGridRow = {
  id: number;
  username: string;
  nickname?: string;
  homePath?: string;
  status: number;
  createTime?: string;
};

// 用户表单弹窗（新增/编辑共用）
const [UserFormModal, userFormApi] = useVbenModal({
  connectedComponent: UserForm,
});

// 列定义 + proxyConfig（接后端分页）
const gridOptions: VxeTableGridOptions<VxeGridRow> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'id', title: 'ID', visible: false },
    { field: 'username', title: '用户名', minWidth: 140 },
    { field: 'nickname', title: '昵称', minWidth: 140 },
    { field: 'homePath', title: '首页', minWidth: 140 },
    {
      field: 'status',
      title: '状态',
      width: 90,
      formatter: ({ cellValue }) => (cellValue === 0 ? '正常' : '停用'),
    },
    { title: '操作', width: 200, fixed: 'right', slots: { default: 'action' } },
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
    refresh: { code: 'query' },
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
      {
        component: 'Select',
        componentProps: {
          placeholder: '请选择状态',
          clearable: true,
          options: [
            { label: '正常', value: 0 },
            { label: '停用', value: 1 },
          ],
        },
        fieldName: 'status',
        label: '状态',
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
  await ElMessageBox.confirm(`确认删除用户「${row.username}」？`, '提示', {
    type: 'warning',
  });
  await deleteUserApi(row.id);
  ElMessage.success('删除成功');
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
        <VbenButton variant="default" @click="openCreate">新增用户</VbenButton>
      </template>
      <template #action="{ row }">
        <VbenButton variant="link" size="sm" @click="openEdit(row)"> 编辑 </VbenButton>
        <VbenButton
          variant="link"
          size="sm"
          class="text-destructive"
          @click="remove(row as VxeGridRow)"
        >
          删除
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
