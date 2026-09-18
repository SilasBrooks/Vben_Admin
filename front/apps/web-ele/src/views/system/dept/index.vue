<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { DeptNode } from '#/api/system/dept';

import { Page, VbenButton, useVbenModal } from '@vben/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import { deleteDeptApi, getDeptTreeApi } from '#/api/system/dept';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { $t } from '#/locales';

import DeptForm from './dept-form.vue';

const [DeptFormModal, deptFormApi] = useVbenModal({
  connectedComponent: DeptForm,
});

const gridOptions: VxeTableGridOptions<DeptNode> = {
  rowConfig: { keyField: 'id', isHover: true },
  // 后端 /system/dept/list 直接返回嵌套 children 的树，无需 transform 重组
  treeConfig: {
    rowField: 'id',
    childrenField: 'children',
    expandAll: true,
  },
  columns: [
    { field: 'deptName', title: $t('system.dept.deptName'), minWidth: 220, treeNode: true },
    { field: 'leader', title: $t('system.dept.leader'), width: 120 },
    { field: 'orderNum', title: $t('system.common.sort'), width: 80 },
    {
      field: 'status',
      title: $t('system.common.status'),
      width: 90,
      formatter: ({ cellValue }) =>
        cellValue === 0
          ? $t('system.common.enabled')
          : $t('system.common.disabled'),
    },
    { field: 'remark', title: $t('system.common.remark'), minWidth: 160 },
    { field: 'createTime', title: $t('system.common.createdAt'), width: 170 },
    { title: $t('system.common.actions'), width: 220, fixed: 'right', slots: { default: 'action' } },
  ],
  pagerConfig: { enabled: false },
  proxyConfig: {
    ajax: {
      query: async () => {
        return await getDeptTreeApi();
      },
    },
  },
  toolbarConfig: {
    custom: true,
    refresh: true,
    refreshOptions: { code: 'query' },
  },
};

const [Grid, gridApi] = useVbenVxeGrid({ gridOptions });

function openCreate(parentId = 0) {
  deptFormApi.setData({ parentId }).open();
}

function openEdit(row: DeptNode) {
  deptFormApi.setData({ id: row.id }).open();
}

async function remove(row: DeptNode) {
  await ElMessageBox.confirm(
    $t('system.dept.deleteConfirm', { name: row.deptName }),
    $t('system.common.notice'),
    {
      type: 'warning',
    },
  );
  await deleteDeptApi(row.id);
  ElMessage.success($t('system.common.deleteSuccess'));
  gridApi.reload();
}

function onFormSaved() {
  gridApi.reload();
}
</script>

<template>
  <Page auto-content-height>
    <DeptFormModal @saved="onFormSaved" />
    <Grid>
      <template #toolbar-actions>
        <VbenButton v-access:code="'System:Dept:Add'" variant="default" @click="openCreate(0)">
          {{ $t('system.dept.addRoot') }}
        </VbenButton>
      </template>
      <template #action="{ row }">
        <VbenButton
          v-access:code="'System:Dept:Add'"
          variant="link"
          size="sm"
          @click="openCreate((row as DeptNode).id)"
        >
          {{ $t('system.dept.addChild') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Dept:Edit'"
          variant="link"
          size="sm"
          @click="openEdit(row as DeptNode)"
        >
          {{ $t('system.common.edit') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Dept:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          @click="remove(row as DeptNode)"
        >
          {{ $t('system.common.delete') }}
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
