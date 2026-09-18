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

const [Form, formApi] = useVbenForm({
  schema: [],
  showDefaultActions: false,
});

async function init() {
  const data = modalApi.getData<{ id?: number }>();
  isEdit.value = !!data?.id;
  editId.value = data?.id;
  modalApi.setState({
    title: isEdit.value ? $t('system.dict.editDictType') : $t('system.dict.addDictType'),
  });

  const schema: VbenFormSchema[] = [
    {
      fieldName: 'dictName',
      label: $t('system.dict.dictName'),
      component: 'Input',
      componentProps: {
        placeholder: $t('system.dict.dictNamePlaceholder'),
      },
      rules: z.string().min(1, { message: $t('system.dict.enterDictName') }),
    },
    {
      fieldName: 'dictType',
      label: $t('system.dict.typeKey'),
      component: 'Input',
      componentProps: {
        placeholder: $t('system.dict.typeKeyPlaceholder'),
      },
      rules: z
        .string()
        .min(1, { message: $t('system.dict.enterTypeKey') })
        .regex(/^[\w:]+$/, { message: $t('system.dict.typeKeyRule') }),
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
