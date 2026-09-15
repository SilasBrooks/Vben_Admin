<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { DictDataItem } from '#/api/system/dict';

import { nextTick, ref } from 'vue';

import { VbenButton, useVbenModal } from '@vben/common-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteDictDataApi, getDictDataListApi } from '#/api/system/dict';
import { clearDictCache } from '#/hooks/use-dict';

import DictDataForm from './dict-data-form.vue';

const emit = defineEmits<{ changed: [] }>();

const dictTypeName = ref('');
const dictTypeKey = ref('');

const [DictDataFormModal, dictDataFormApi] = useVbenModal({
  connectedComponent: DictDataForm,
});

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ dictName: string; dictType: string }>();
      dictTypeName.value = data?.dictName ?? '';
      dictTypeKey.value = data?.dictType ?? '';
      modalApi.setState({ title: `字典数据 - ${dictTypeName.value}` });
      // 弹窗内容（Grid）此刻尚未挂载，等渲染完成后再触发查询
      nextTick(() => gridApi.query());
    }
  },
});

const gridOptions: VxeTableGridOptions<DictDataItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'dictLabel', title: '标签', minWidth: 120 },
    { field: 'dictValue', title: '键值', minWidth: 120 },
    { field: 'sortNum', title: '排序', width: 70 },
    {
      field: 'status',
      title: '状态',
      width: 80,
      formatter: ({ cellValue }) => (cellValue === 0 ? '正常' : '停用'),
    },
    { field: 'remark', title: '备注', minWidth: 120 },
    { title: '操作', width: 120, fixed: 'right', slots: { default: 'action' } },
  ],
  pagerConfig: { enabled: true },
  proxyConfig: {
    ajax: {
      query: async ({ page }) => {
        return await getDictDataListApi({
          dictType: dictTypeKey.value,
          pageNo: page.currentPage,
          pageSize: page.pageSize,
        });
      },
    },
  },
};

const [Grid, gridApi] = useVbenVxeGrid({ gridOptions });

function openCreate() {
  dictDataFormApi.setData({ dictType: dictTypeKey.value }).open();
}

function openEdit(row: DictDataItem) {
  dictDataFormApi.setData({ id: row.id }).open();
}

async function remove(row: DictDataItem) {
  await ElMessageBox.confirm(
    `确认删除字典数据「${row.dictLabel}」？`,
    '提示',
    { type: 'warning' },
  );
  await deleteDictDataApi(row.id);
  ElMessage.success('删除成功');
  afterChanged();
}

function afterChanged() {
  clearDictCache(dictTypeKey.value);
  gridApi.query();
  emit('changed');
}
</script>

<template>
  <Modal class="w-[860px]">
    <DictDataFormModal @saved="afterChanged" />
    <Grid>
      <template #toolbar-actions>
        <VbenButton
          v-access:code="'System:Dict:Add'"
          variant="default"
          @click="openCreate"
        >
          新增数据
        </VbenButton>
      </template>
      <template #action="{ row }">
        <VbenButton
          v-access:code="'System:Dict:Edit'"
          variant="link"
          size="sm"
          @click="openEdit(row as DictDataItem)"
        >
          编辑
        </VbenButton>
        <VbenButton
          v-access:code="'System:Dict:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          @click="remove(row as DictDataItem)"
        >
          删除
        </VbenButton>
      </template>
    </Grid>
  </Modal>
</template>
