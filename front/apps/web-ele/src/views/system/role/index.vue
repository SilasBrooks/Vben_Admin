<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { Page, VbenButton, useVbenModal } from '@vben/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  deleteRoleApi,
  getRoleListApi,
  type RoleItem,
} from '#/api/system/role';
import { useVbenVxeGrid } from '#/adapter/vxe-table';

import RoleForm from './role-form.vue';
import RoleMenuAuth from './role-menu-auth.vue';

// 角色表单弹窗（新增/编辑共用）
const [RoleFormModal, roleFormApi] = useVbenModal({
  connectedComponent: RoleForm,
});

// 菜单授权弹窗
const [RoleMenuAuthModal, roleMenuAuthApi] = useVbenModal({
  connectedComponent: RoleMenuAuth,
});

const gridOptions: VxeTableGridOptions<RoleItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'id', title: 'ID', visible: false },
    { field: 'roleKey', title: '角色标识', minWidth: 140 },
    { field: 'roleName', title: '角色名称', minWidth: 140 },
    { field: 'sortNum', title: '排序', width: 80 },
    {
      field: 'status',
      title: '状态',
      width: 90,
      formatter: ({ cellValue }) => (cellValue === 0 ? '正常' : '停用'),
    },
    { title: '操作', width: 280, fixed: 'right', slots: { default: 'action' } },
  ],
  pagerConfig: { enabled: true },
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        return await getRoleListApi({
          pageNo: page.currentPage,
          pageSize: page.pageSize,
          roleName: formValues?.roleName,
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
        componentProps: { placeholder: '请输入角色名称' },
        fieldName: 'roleName',
        label: '角色名称',
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
  roleFormApi.setData({}).open();
}

function openEdit(row: RoleItem) {
  roleFormApi.setData({ id: row.id }).open();
}

function openMenuAuth(row: RoleItem) {
  roleMenuAuthApi.setData({ id: row.id }).open();
}

async function remove(row: RoleItem) {
  await ElMessageBox.confirm(`确认删除角色「${row.roleName}」？`, '提示', {
    type: 'warning',
  });
  await deleteRoleApi(row.id);
  ElMessage.success('删除成功');
  gridApi.reload();
}

function onFormSaved() {
  gridApi.reload();
}
</script>

<template>
  <Page auto-content-height>
    <RoleFormModal @saved="onFormSaved" />
    <RoleMenuAuthModal @saved="onFormSaved" />
    <Grid>
      <template #toolbar-actions>
        <VbenButton variant="default" @click="openCreate">新增角色</VbenButton>
      </template>
      <template #action="{ row }">
        <VbenButton variant="link" size="sm" @click="openEdit(row as RoleItem)">
          编辑
        </VbenButton>
        <VbenButton
          variant="link"
          size="sm"
          @click="openMenuAuth(row as RoleItem)"
        >
          分配菜单
        </VbenButton>
        <VbenButton
          variant="link"
          size="sm"
          class="text-destructive"
          @click="remove(row as RoleItem)"
        >
          删除
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
