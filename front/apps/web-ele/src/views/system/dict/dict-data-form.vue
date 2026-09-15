<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';
import type { DictDataItem } from '#/api/system/dict';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { useVbenForm, z } from '#/adapter/form';
import {
  createDictDataApi,
  updateDictDataApi,
} from '#/api/system/dict';

const emit = defineEmits<{ saved: [] }>();

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen: boolean) {
    if (isOpen) {
      init();
    }
  },
  onConfirm: handleSubmit,
});

const isEdit = ref(false);
const editId = ref<number>();
const dictType = ref('');

const [Form, formApi] = useVbenForm({
  schema: [],
  showDefaultActions: false,
});

async function init() {
  const data = modalApi.getData<{ id?: number; dictType: string }>();
  isEdit.value = !!data?.id;
  editId.value = data?.id;
  dictType.value = data?.dictType ?? '';
  modalApi.setState({
    title: isEdit.value ? '编辑字典数据' : '新增字典数据',
  });

  const schema: VbenFormSchema[] = [
    {
      fieldName: 'dictLabel',
      label: '标签',
      component: 'Input',
      componentProps: { placeholder: '显示名，如 电子产品' },
      rules: z.string().min(1, { message: '请输入标签' }),
    },
    {
      fieldName: 'dictValue',
      label: '键值',
      component: 'Input',
      componentProps: { placeholder: '如 electronic（同类型下唯一）' },
      rules: z.string().min(1, { message: '请输入键值' }),
    },
    {
      fieldName: 'sortNum',
      label: '排序',
      component: 'InputNumber',
      defaultValue: 0,
      componentProps: { min: 0 },
    },
    {
      fieldName: 'status',
      label: '状态',
      component: 'RadioGroup',
      componentProps: {
        options: [
          { label: '正常', value: 0 },
          { label: '停用', value: 1 },
        ],
      },
      defaultValue: 0,
    },
    {
      fieldName: 'remark',
      label: '备注',
      component: 'Input',
      componentProps: { type: 'textarea', rows: 2 },
    },
  ];
  formApi.setState({ schema });

  await formApi.resetForm();
}

async function handleSubmit() {
  const values = (await formApi.getValues()) as Partial<DictDataItem>;
  modalApi.lock();
  try {
    if (isEdit.value) {
      await updateDictDataApi({ id: editId.value, ...values });
    } else {
      await createDictDataApi({ dictType: dictType.value, ...values });
    }
    emit('saved');
    modalApi.close();
  } finally {
    modalApi.unlock();
  }
}
</script>

<template>
  <Modal>
    <Form />
  </Modal>
</template>
