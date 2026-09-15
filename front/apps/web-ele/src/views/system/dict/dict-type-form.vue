<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';
import type { DictTypeItem } from '#/api/system/dict';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { useVbenForm, z } from '#/adapter/form';
import {
  createDictTypeApi,
  getDictTypeListApi,
  updateDictTypeApi,
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

const [Form, formApi] = useVbenForm({
  schema: [],
  showDefaultActions: false,
});

async function init() {
  const data = modalApi.getData<{ id?: number }>();
  isEdit.value = !!data?.id;
  editId.value = data?.id;
  modalApi.setState({
    title: isEdit.value ? '编辑字典类型' : '新增字典类型',
  });

  const schema: VbenFormSchema[] = [
    {
      fieldName: 'dictName',
      label: '字典名称',
      component: 'Input',
      componentProps: { placeholder: '如 库存类型' },
      rules: z.string().min(1, { message: '请输入字典名称' }),
    },
    {
      fieldName: 'dictType',
      label: '类型键',
      component: 'Input',
      componentProps: { placeholder: '全局唯一，如 wsm_stock_type' },
      rules: z
        .string()
        .min(1, { message: '请输入类型键' })
        .regex(/^[\w:]+$/, { message: '仅支持字母/数字/下划线/冒号' }),
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
  if (isEdit.value && data?.id) {
    const list = await getDictTypeListApi();
    const detail = list.find((item) => item.id === data.id);
    if (detail) {
      await formApi.setValues(detail);
    }
  }
}

async function handleSubmit() {
  const values = (await formApi.getValues()) as Partial<DictTypeItem>;
  modalApi.lock();
  try {
    if (isEdit.value) {
      await updateDictTypeApi({ id: editId.value, ...values });
    } else {
      await createDictTypeApi(values);
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
