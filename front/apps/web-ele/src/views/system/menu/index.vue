<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MenuNode } from '#/api/system/menu';

import { Page, VbenButton, useVbenModal } from '@vben/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import { deleteMenuApi, getMenuTreeApi } from '#/api/system/menu';
import { useVbenVxeGrid } from '#/adapter/vxe-table';

import MenuForm from './menu-form.vue';

const [MenuFormModal, menuFormApi] = useVbenModal({
  connectedComponent: MenuForm,
});

const gridOptions: VxeTableGridOptions<MenuNode> = {
  rowConfig: { keyField: 'id', isHover: true },
  // 后端 /system/menu/list 直接返回嵌套 children 的树，无需 transform 重组
  treeConfig: {
    rowField: 'id',
    childrenField: 'children',
    expandAll: true,
  },
  columns: [
    { field: 'title', title: '显示名称', minWidth: 200, treeNode: true },
    { field: 'menuName', title: '菜单标识', minWidth: 140 },
    {
      field: 'menuType',
      title: '类型',
      width: 80,
      formatter: ({ cellValue }) =>
        cellValue === 'M' ? '目录' : cellValue === 'C' ? '菜单' : '按钮',
    },
    { field: 'icon', title: '图标', width: 120 },
    { field: 'orderNum', title: '排序', width: 70 },
    { field: 'path', title: '路由路径', minWidth: 160 },
    { field: 'component', title: '组件路径', minWidth: 180 },
    { field: 'perm', title: '权限码', minWidth: 160 },
    {
      field: 'visible',
      title: '显示',
      width: 70,
      formatter: ({ cellValue }) => (cellValue === 0 ? '显示' : '隐藏'),
    },
    { title: '操作', width: 220, fixed: 'right', slots: { default: 'action' } },
  ],
  pagerConfig: { enabled: false },
  proxyConfig: {
    ajax: {
      query: async () => {
        return await getMenuTreeApi();
      },
    },
  },
  toolbarConfig: {
    custom: true,
    refresh: { code: 'query' },
  },
};

const [Grid, gridApi] = useVbenVxeGrid({ gridOptions });

function openCreate(parentId = 0) {
  menuFormApi.setData({ parentId }).open();
}

function openEdit(row: MenuNode) {
  menuFormApi.setData({ id: row.id }).open();
}

async function remove(row: MenuNode) {
  await ElMessageBox.confirm(`确认删除菜单「${row.title}」？`, '提示', {
    type: 'warning',
  });
  await deleteMenuApi(row.id);
  ElMessage.success('删除成功');
  // 菜单变更后需要让前端路由重新加载
  ElMessage.info('菜单已变更，1.5 秒后自动刷新页面以加载新路由...');
  setTimeout(() => window.location.reload(), 1500);
}

function onFormSaved() {
  gridApi.reload();
  // 菜单变更后刷新页面以让前端路由重新生成
  ElMessage.info('菜单已变更，1.5 秒后自动刷新页面以加载新路由...');
  setTimeout(() => window.location.reload(), 1500);
}
</script>

<template>
  <Page auto-content-height>
    <MenuFormModal @saved="onFormSaved" />
    <Grid>
      <template #toolbar-actions>
        <VbenButton v-access:code="'System:Menu:Add'" variant="default" @click="openCreate(0)">
          新增根菜单
        </VbenButton>
      </template>
      <template #action="{ row }">
        <VbenButton
          v-access:code="'System:Menu:Add'"
          variant="link"
          size="sm"
          @click="openCreate((row as MenuNode).id)"
        >
          新增子项
        </VbenButton>
        <VbenButton
          v-access:code="'System:Menu:Edit'"
          variant="link"
          size="sm"
          @click="openEdit(row as MenuNode)"
        >
          编辑
        </VbenButton>
        <VbenButton
          v-access:code="'System:Menu:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          @click="remove(row as MenuNode)"
        >
          删除
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
