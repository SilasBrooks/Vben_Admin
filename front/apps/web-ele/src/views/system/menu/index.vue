<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MenuNode } from '#/api/system/menu';

import { Page, VbenButton, useVbenModal } from '@vben/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import { deleteMenuApi, getMenuTreeApi } from '#/api/system/menu';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { $t } from '#/locales';

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
    { field: 'title', title: $t('system.menu.displayName'), minWidth: 200, treeNode: true },
    { field: 'menuName', title: $t('system.menu.menuName'), minWidth: 140 },
    {
      field: 'menuType',
      title: $t('system.menu.type'),
      width: 80,
      formatter: ({ cellValue }) =>
        cellValue === 'M'
          ? $t('system.menu.typeDirectory')
          : cellValue === 'C'
            ? $t('system.menu.typeMenu')
            : $t('system.menu.typeButton'),
    },
    { field: 'icon', title: $t('system.menu.icon'), width: 120 },
    { field: 'orderNum', title: $t('system.common.sort'), width: 70 },
    { field: 'path', title: $t('system.menu.routePath'), minWidth: 160 },
    { field: 'component', title: $t('system.menu.componentPath'), minWidth: 180 },
    { field: 'perm', title: $t('system.menu.perm'), minWidth: 160 },
    {
      field: 'visible',
      title: $t('system.menu.show'),
      width: 70,
      formatter: ({ cellValue }) =>
        cellValue === 0
          ? $t('system.menu.show')
          : $t('system.menu.hide'),
    },
    { title: $t('system.common.actions'), width: 220, fixed: 'right', slots: { default: 'action' } },
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
    refresh: true,
    refreshOptions: { code: 'query' },
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
  await ElMessageBox.confirm(
    $t('system.menu.deleteConfirm', { name: row.title }),
    $t('system.common.notice'),
    {
      type: 'warning',
    },
  );
  await deleteMenuApi(row.id);
  ElMessage.success($t('system.common.deleteSuccess'));
  // 菜单变更后需要让前端路由重新加载
  ElMessage.info($t('system.menu.reloadNotice'));
  setTimeout(() => window.location.reload(), 1500);
}

function onFormSaved() {
  gridApi.reload();
  // 菜单变更后刷新页面以让前端路由重新生成
  ElMessage.info($t('system.menu.reloadNotice'));
  setTimeout(() => window.location.reload(), 1500);
}
</script>

<template>
  <Page auto-content-height>
    <MenuFormModal @saved="onFormSaved" />
    <Grid>
      <template #toolbar-actions>
        <VbenButton v-access:code="'System:Menu:Add'" variant="default" @click="openCreate(0)">
          {{ $t('system.menu.addRoot') }}
        </VbenButton>
      </template>
      <template #action="{ row }">
        <VbenButton
          v-access:code="'System:Menu:Add'"
          variant="link"
          size="sm"
          @click="openCreate((row as MenuNode).id)"
        >
          {{ $t('system.menu.addChild') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Menu:Edit'"
          variant="link"
          size="sm"
          @click="openEdit(row as MenuNode)"
        >
          {{ $t('system.common.edit') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Menu:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          @click="remove(row as MenuNode)"
        >
          {{ $t('system.common.delete') }}
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
