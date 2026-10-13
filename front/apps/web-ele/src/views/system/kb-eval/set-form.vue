<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { useVbenForm, z } from '#/adapter/form';
import type { KbEvalSetItem } from '#/api/system/kbEval';
import { createKbEvalSetApi, updateKbEvalSetApi } from '#/api/system/kbEval';
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

const [Form, formApi] = useVbenForm({
  schema: [],
  showDefaultActions: false,
});

async function init() {
  const data = modalApi.getData<Partial<KbEvalSetItem>>();
  isEdit.value = !!data?.id;
  modalApi.setState({
    title: isEdit.value
      ? $t('kbEval.setFormTitleEdit')
      : $t('kbEval.setFormTitleAdd'),
  });

  const schema: VbenFormSchema[] = [
    {
      fieldName: 'name',
      label: $t('kbEval.setName'),
      component: 'Input',
      componentProps: {
        placeholder: $t('kbEval.setNamePlaceholder'),
        maxlength: 64,
      },
      rules: z.string().min(1, { message: $t('kbEval.nameRequiredHint') }),
    },
    {
      fieldName: 'description',
      label: $t('kbEval.setDescription'),
      component: 'Input',
      componentProps: {
        type: 'textarea',
        rows: 2,
        maxlength: 255,
        placeholder: $t('kbEval.setDescriptionPlaceholder'),
      },
    },
  ];
  formApi.setState({ schema });

  await formApi.resetForm();
  if (isEdit.value && data) {
    await formApi.setValues({
      name: data.name,
      description: data.description ?? '',
    });
  }
}

async function handleSubmit() {
  const values = await formApi.getValues();
  modalApi.lock();
  try {
    const payload = {
      description: (values.description as string) || undefined,
      name: String(values.name ?? '').trim(),
    };
    if (isEdit.value) {
      const id = (modalApi.getData() as { id: number }).id;
      await updateKbEvalSetApi(id, payload);
    } else {
      await createKbEvalSetApi(payload);
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
