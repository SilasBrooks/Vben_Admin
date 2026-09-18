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
import { $t } from '#/locales';

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

// 数据范围中文映射（1全部 2自定义部门 3本部门 4本部门及以下 5仅本人）
const DATA_SCOPE_LABELS: Record<string, string> = {
  1: $t('system.role.scopeAll'),
  2: $t('system.role.scopeCustom'),
  3: $t('system.role.scopeDept'),
  4: $t('system.role.scopeDeptAndBelow'),
  5: $t('system.role.scopeSelf'),
};

const gridOptions: VxeTableGridOptions<RoleItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'id', title: 'ID', visible: false },
    { field: 'roleKey', title: $t('system.role.roleKey'), minWidth: 140 },
    { field: 'roleName', title: $t('system.role.roleName'), minWidth: 140 },
    { field: 'sortNum', title: $t('system.common.sort'), width: 80 },
    {
      field: 'dataScope',
      title: $t('system.role.dataScope'),
      width: 110,
      formatter: ({ cellValue }) =>
        DATA_SCOPE_LABELS[cellValue as string] ?? $t('system.role.scopeSelf'),
    },
    {
      field: 'status',
      title: $t('system.common.status'),
      width: 90,
      formatter: ({ cellValue }) =>
        cellValue === 0
          ? $t('system.common.enabled')
          : $t('system.common.disabled'),
    },
    { title: $t('system.common.actions'), width: 280, fixed: 'right', slots: { default: 'action' } },
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
        componentProps: { placeholder: $t('system.role.enterRoleName') },
        fieldName: 'roleName',
        label: $t('system.role.roleName'),
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
  roleFormApi.setData({}).open();
}

function openEdit(row: RoleItem) {
  roleFormApi.setData({ id: row.id }).open();
}

function openMenuAuth(row: RoleItem) {
  roleMenuAuthApi.setData({ id: row.id }).open();
}

async function remove(row: RoleItem) {
  await ElMessageBox.confirm(
    $t('system.role.deleteConfirm', { name: row.roleName }),
    $t('system.common.notice'),
    {
      type: 'warning',
    },
  );
  await deleteRoleApi(row.id);
  ElMessage.success($t('system.common.deleteSuccess'));
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
        <VbenButton
          v-access:code="'System:Role:Add'"
          variant="default"
          @click="openCreate"
        >
          {{ $t('system.role.add') }}
        </VbenButton>
      </template>
      <template #action="{ row }">
        <VbenButton
          v-access:code="'System:Role:Edit'"
          variant="link"
          size="sm"
          @click="openEdit(row as RoleItem)"
        >
          {{ $t('system.common.edit') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Role:Auth'"
          variant="link"
          size="sm"
          @click="openMenuAuth(row as RoleItem)"
        >
          {{ $t('system.role.assignMenus') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Role:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          @click="remove(row as RoleItem)"
        >
          {{ $t('system.common.delete') }}
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
