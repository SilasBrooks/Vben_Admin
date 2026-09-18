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
import { $t } from '#/locales';

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
    title: isEdit.value ? $t('system.dict.editDictData') : $t('system.dict.addDictData'),
  });

  const schema: VbenFormSchema[] = [
    {
      fieldName: 'dictLabel',
      label: $t('system.dict.label'),
      component: 'Input',
      componentProps: {
        placeholder: $t('system.dict.labelPlaceholder'),
      },
      rules: z.string().min(1, { message: $t('system.dict.enterLabel') }),
    },
    {
      fieldName: 'dictValue',
      label: $t('system.dict.value'),
      component: 'Input',
      componentProps: {
        placeholder: $t('system.dict.valuePlaceholder'),
      },
      rules: z.string().min(1, { message: $t('system.dict.enterValue') }),
    },
    {
      fieldName: 'sortNum',
      label: $t('system.common.sort'),
      component: 'InputNumber',
      defaultValue: 0,
      componentProps: { min: 0 },
    },
    {
      fieldName: 'status',
      label: $t('system.common.status'),
      component: 'RadioGroup',
      componentProps: {
        options: [
          { label: $t('system.common.enabled'), value: 0 },
          { label: $t('system.common.disabled'), value: 1 },
        ],
      },
      defaultValue: 0,
    },
    {
      fieldName: 'remark',
      label: $t('system.common.remark'),
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
