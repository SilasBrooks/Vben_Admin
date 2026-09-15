<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { DeptNode } from '#/api/system/dept';

import { Page, VbenButton, useVbenModal } from '@vben/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import { deleteDeptApi, getDeptTreeApi } from '#/api/system/dept';
import { useVbenVxeGrid } from '#/adapter/vxe-table';

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
    { field: 'deptName', title: '部门名称', minWidth: 220, treeNode: true },
    { field: 'leader', title: '负责人', width: 120 },
    { field: 'orderNum', title: '排序', width: 80 },
    {
      field: 'status',
      title: '状态',
      width: 90,
      formatter: ({ cellValue }) => (cellValue === 0 ? '正常' : '停用'),
    },
    { field: 'remark', title: '备注', minWidth: 160 },
    { field: 'createTime', title: '创建时间', width: 170 },
    { title: '操作', width: 220, fixed: 'right', slots: { default: 'action' } },
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
  await ElMessageBox.confirm(`确认删除部门「${row.deptName}」？`, '提示', {
    type: 'warning',
  });
  await deleteDeptApi(row.id);
  ElMessage.success('删除成功');
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
          新增根部门
        </VbenButton>
      </template>
      <template #action="{ row }">
        <VbenButton
          v-access:code="'System:Dept:Add'"
          variant="link"
          size="sm"
          @click="openCreate((row as DeptNode).id)"
        >
          新增子部门
        </VbenButton>
        <VbenButton
          v-access:code="'System:Dept:Edit'"
          variant="link"
          size="sm"
          @click="openEdit(row as DeptNode)"
        >
          编辑
        </VbenButton>
        <VbenButton
          v-access:code="'System:Dept:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          @click="remove(row as DeptNode)"
        >
          删除
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
