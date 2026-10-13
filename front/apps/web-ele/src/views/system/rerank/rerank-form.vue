<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { useVbenForm, z } from '#/adapter/form';
import type { RerankItem } from '#/api/system/rerank';
import { createRerankApi, updateRerankApi } from '#/api/system/rerank';
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

// schema 在 init 时根据 isEdit 动态生成（apiKey 占位文案随新增/编辑切换）
const [Form, formApi] = useVbenForm({
  schema: [],
  showDefaultActions: false,
});

async function init() {
  const data = modalApi.getData<Partial<RerankItem>>();
  isEdit.value = !!data?.id;
  modalApi.setState({
    title: isEdit.value ? $t('rerank.formTitleEdit') : $t('rerank.formTitleAdd'),
  });

  const schema: VbenFormSchema[] = [
    {
      fieldName: 'name',
      label: $t('rerank.name'),
      component: 'Input',
      componentProps: {
        placeholder: $t('rerank.namePlaceholder'),
        maxlength: 64,
      },
      rules: z.string().min(1, { message: $t('rerank.requiredHint') }),
    },
    {
      fieldName: 'baseUrl',
      label: $t('rerank.baseUrl'),
      component: 'Input',
      componentProps: {
        placeholder: $t('rerank.baseUrlPlaceholder'),
        maxlength: 255,
      },
      rules: z.string().min(1, { message: $t('rerank.requiredHint') }),
    },
    {
      fieldName: 'apiKey',
      label: $t('rerank.apiKey'),
      component: 'Input',
      componentProps: {
        type: 'password',
        showPassword: true,
        autocomplete: 'new-password',
        placeholder: isEdit.value
          ? $t('rerank.apiKeyEditPlaceholder')
          : $t('rerank.apiKeyPlaceholder'),
      },
    },
    {
      fieldName: 'model',
      label: $t('rerank.model'),
      component: 'Input',
      componentProps: {
        placeholder: $t('rerank.modelPlaceholder'),
        maxlength: 64,
      },
      rules: z.string().min(1, { message: $t('rerank.requiredHint') }),
    },
    {
      fieldName: 'timeoutSeconds',
      label: $t('rerank.timeout'),
      component: 'Select',
      componentProps: {
        options: [15, 30, 60, 120].map((s) => ({ label: s, value: s })),
      },
      defaultValue: 30,
    },
    {
      fieldName: 'enabled',
      label: $t('rerank.enabled'),
      component: 'RadioGroup',
      componentProps: {
        options: [
          { label: $t('rerank.enabledOn'), value: 1 },
          { label: $t('rerank.enabledOff'), value: 0 },
        ],
      },
      defaultValue: 1,
    },
    {
      fieldName: 'remark',
      label: $t('rerank.remark'),
      component: 'Input',
      componentProps: {
        type: 'textarea',
        rows: 2,
        maxlength: 255,
        placeholder: $t('rerank.remarkPlaceholder'),
      },
    },
  ];
  formApi.setState({ schema });

  await formApi.resetForm();
  if (isEdit.value && data) {
    // apiKey 恒置空：编辑留空 = 保持原 Key 不变
    await formApi.setValues({
      name: data.name,
      baseUrl: data.baseUrl,
      apiKey: '',
      model: data.model,
      timeoutSeconds: data.timeoutSeconds || 30,
      enabled: data.enabled ?? 1,
      remark: data.remark ?? '',
    });
  }
}

async function handleSubmit() {
  const values = await formApi.getValues();
  modalApi.lock();
  try {
    const payload = {
      apiKey: (values.apiKey as string) || '',
      baseUrl: String(values.baseUrl ?? '').trim(),
      enabled: (values.enabled ?? 1) as 0 | 1,
      model: String(values.model ?? '').trim(),
      name: String(values.name ?? '').trim(),
      remark: (values.remark as string) || null,
      timeoutSeconds: (values.timeoutSeconds as number) || 30,
    };
    if (isEdit.value) {
      const id = (modalApi.getData() as { id: number }).id;
      await updateRerankApi(id, payload);
    } else {
      await createRerankApi(payload);
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
