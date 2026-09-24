<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { MenuNode } from '#/api/system/menu';

import { nextTick, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page, VbenButton, useVbenModal } from '@vben/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import { deleteMenuApi, getMenuTreeApi } from '#/api/system/menu';
import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { $t } from '#/locales';
import { refreshAccess } from '#/router/refresh-access';

import MenuForm from './menu-form.vue';

const router = useRouter();
const syncFailed = ref(false);
const syncing = ref(false);

const [MenuFormModal, menuFormApi] = useVbenModal({
  connectedComponent: MenuForm,
});

const gridOptions: VxeTableGridOptions<MenuNode> = {
  id: 'table.system.menu',
  rowConfig: { keyField: 'id', isHover: true },
  // 后端 /system/menu/list 直接返回嵌套 children 的树，无需 transform 重组
  treeConfig: {
    rowField: 'id',
    childrenField: 'children',
    expandAll: true,
  },
  columns: [
    { field: 'title', title: $t('system.menu.displayName'), minWidth: 200, treeNode: true, slots: { default: 'title' } },
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
    { field: '__actions', title: $t('system.common.actions'), width: 220, fixed: 'right', slots: { default: 'action' } },
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
  await syncMenus();
}

async function onFormSaved() {
  ElMessage.success($t('system.common.saveSuccess'));
  await syncMenus();
}

async function syncMenus() {
  syncing.value = true;
  syncFailed.value = false;
  try {
    await nextTick();
    await Promise.all([gridApi.query(), refreshAccess(router)]);
  } catch {
    syncFailed.value = true;
    ElMessage.warning($t('system.menu.syncFailed'));
  } finally {
    syncing.value = false;
  }
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
        <VbenButton v-if="syncFailed" variant="outline" :loading="syncing" @click="syncMenus">
          {{ $t('system.menu.retrySync') }}
        </VbenButton>
      </template>
      <template #title="{ row }">
        <div class="leading-tight">
          <div>{{ (row as MenuNode).title.startsWith('page.') ? $t((row as MenuNode).title) : (row as MenuNode).title }}</div>
          <div
            v-if="(row as MenuNode).title.startsWith('page.')"
            class="text-muted-foreground text-xs"
          >
            {{ (row as MenuNode).title }}
          </div>
        </div>
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
