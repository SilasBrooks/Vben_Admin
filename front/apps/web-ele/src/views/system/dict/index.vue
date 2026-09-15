<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { DictTypeItem } from '#/api/system/dict';

import { Page, VbenButton, useVbenModal } from '@vben/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteDictTypeApi, getDictTypeListApi } from '#/api/system/dict';
import { clearDictCache } from '#/hooks/use-dict';

import DictDataModalComponent from './dict-data-modal.vue';
import DictTypeForm from './dict-type-form.vue';

const [DictTypeFormModal, dictTypeFormApi] = useVbenModal({
  connectedComponent: DictTypeForm,
});

const [DictDataModal, dictDataModalApi] = useVbenModal({
  connectedComponent: DictDataModalComponent,
});

const gridOptions: VxeTableGridOptions<DictTypeItem> = {
  rowConfig: { keyField: 'id', isHover: true },
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'dictName', title: '字典名称', minWidth: 160 },
    { field: 'dictType', title: '类型键', minWidth: 180 },
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
    `确认删除字典类型「${row.dictName}」？其下全部字典数据将一并删除，不可恢复！`,
    '警告',
    { type: 'warning' },
  );
  await deleteDictTypeApi(row.id);
  clearDictCache(row.dictType);
  ElMessage.success('删除成功');
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
          新增类型
        </VbenButton>
      </template>
      <template #action="{ row }">
        <VbenButton variant="link" size="sm" @click="openData(row as DictTypeItem)">
          数据
        </VbenButton>
        <VbenButton
          v-access:code="'System:Dict:Edit'"
          variant="link"
          size="sm"
          @click="openEdit(row as DictTypeItem)"
        >
          编辑
        </VbenButton>
        <VbenButton
          v-access:code="'System:Dict:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          @click="remove(row as DictTypeItem)"
        >
          删除
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
