<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { DictTypeItem } from '#/api/system/dict';

import { Page, VbenButton, useVbenModal } from '@vben/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteDictTypeApi, getDictTypeListApi } from '#/api/system/dict';
import { clearDictCache } from '#/hooks/use-dict';
import { $t } from '#/locales';

import DictDataModalComponent from './dict-data-modal.vue';
import DictTypeForm from './dict-type-form.vue';

const [DictTypeFormModal, dictTypeFormApi] = useVbenModal({
  connectedComponent: DictTypeForm,
});

const [DictDataModal, dictDataModalApi] = useVbenModal({
  connectedComponent: DictDataModalComponent,
});

const gridOptions: VxeTableGridOptions<DictTypeItem> = {
  id: 'table.system.dict',
  rowConfig: { keyField: 'id', isHover: true },
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'dictName', title: $t('system.dict.dictName'), minWidth: 160 },
    { field: 'dictType', title: $t('system.dict.typeKey'), minWidth: 180 },
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
    { field: '__actions', title: $t('system.common.actions'), width: 220, fixed: 'right', slots: { default: 'action' } },
  ],
  pagerConfig: { enabled: false },
  proxyConfig: {
    ajax: {
      query: async () => {
        return await getDictTypeListApi();
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

function openCreate() {
  dictTypeFormApi.setData({}).open();
}

function openEdit(row: DictTypeItem) {
  dictTypeFormApi.setData({ id: row.id }).open();
}

function openData(row: DictTypeItem) {
  dictDataModalApi
    .setData({ dictName: row.dictName, dictType: row.dictType })
    .open();
}

async function remove(row: DictTypeItem) {
  await ElMessageBox.confirm(
    $t('system.dict.deleteTypeConfirm', { name: row.dictName }),
    $t('system.common.warning'),
    { type: 'warning' },
  );
  await deleteDictTypeApi(row.id);
  clearDictCache(row.dictType);
  ElMessage.success($t('system.common.deleteSuccess'));
  gridApi.reload();
}

function onSaved() {
  clearDictCache();
  gridApi.reload();
}
</script>

<template>
  <Page auto-content-height>
    <DictTypeFormModal @saved="onSaved" />
    <DictDataModal />
    <Grid>
      <template #toolbar-actions>
        <VbenButton
          v-access:code="'System:Dict:Add'"
          variant="default"
          @click="openCreate"
        >
          {{ $t('system.dict.addType') }}
        </VbenButton>
      </template>
      <template #action="{ row }">
        <VbenButton variant="link" size="sm" @click="openData(row as DictTypeItem)">
          {{ $t('system.dict.data') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Dict:Edit'"
          variant="link"
          size="sm"
          @click="openEdit(row as DictTypeItem)"
        >
          {{ $t('system.common.edit') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Dict:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          @click="remove(row as DictTypeItem)"
        >
          {{ $t('system.common.delete') }}
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
