<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { useVbenForm, z } from '#/adapter/form';
import type { EmbItem } from '#/api/system/embedding';
import { createEmbApi, updateEmbApi } from '#/api/system/embedding';
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
  const data = modalApi.getData<Partial<EmbItem>>();
  isEdit.value = !!data?.id;
  modalApi.setState({
    title: isEdit.value ? $t('embedding.formTitleEdit') : $t('embedding.formTitleAdd'),
  });

  const schema: VbenFormSchema[] = [
    {
      fieldName: 'name',
      label: $t('embedding.name'),
      component: 'Input',
      componentProps: {
        placeholder: $t('embedding.namePlaceholder'),
        maxlength: 64,
      },
      rules: z.string().min(1, { message: $t('embedding.requiredHint') }),
    },
    {
      fieldName: 'baseUrl',
      label: $t('embedding.baseUrl'),
      component: 'Input',
      componentProps: {
        placeholder: $t('embedding.baseUrlPlaceholder'),
        maxlength: 255,
      },
      rules: z.string().min(1, { message: $t('embedding.requiredHint') }),
    },
    {
      fieldName: 'apiKey',
      label: $t('embedding.apiKey'),
      component: 'Input',
      componentProps: {
        type: 'password',
        showPassword: true,
        autocomplete: 'new-password',
        placeholder: isEdit.value
          ? $t('embedding.apiKeyEditPlaceholder')
          : $t('embedding.apiKeyPlaceholder'),
      },
    },
    {
      fieldName: 'model',
      label: $t('embedding.model'),
      component: 'Input',
      componentProps: {
        placeholder: $t('embedding.modelPlaceholder'),
        maxlength: 64,
      },
      rules: z.string().min(1, { message: $t('embedding.requiredHint') }),
    },
    {
      fieldName: 'protocol',
      label: $t('embedding.protocol'),
      component: 'Select',
      componentProps: {
        options: [
          { label: $t('embedding.protocolStandard'), value: 'standard' },
          { label: $t('embedding.protocolArk'), value: 'ark-multimodal' },
        ],
      },
      help: $t('embedding.protocolHelp'),
      defaultValue: 'standard',
    },
    {
      fieldName: 'timeoutSeconds',
      label: $t('embedding.timeout'),
      component: 'Select',
      componentProps: {
        options: [30, 60, 120, 180].map((s) => ({ label: s, value: s })),
      },
      defaultValue: 60,
    },
    {
      fieldName: 'enabled',
      label: $t('embedding.enabled'),
      component: 'RadioGroup',
      componentProps: {
        options: [
          { label: $t('embedding.enabledOn'), value: 1 },
          { label: $t('embedding.enabledOff'), value: 0 },
        ],
      },
      defaultValue: 1,
    },
    {
      fieldName: 'remark',
      label: $t('embedding.remark'),
      component: 'Input',
      componentProps: {
        type: 'textarea',
        rows: 2,
        maxlength: 255,
        placeholder: $t('embedding.remarkPlaceholder'),
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
      protocol: data.protocol ?? 'standard',
      timeoutSeconds: data.timeoutSeconds || 60,
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
      protocol: ((values.protocol as string) || 'standard') as 'ark-multimodal' | 'standard',
      remark: (values.remark as string) || null,
      timeoutSeconds: (values.timeoutSeconds as number) || 60,
    };
    if (isEdit.value) {
      const id = (modalApi.getData() as { id: number }).id;
      await updateEmbApi(id, payload);
    } else {
      await createEmbApi(payload);
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
