<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { useVbenForm, z } from '#/adapter/form';
import type { LlmItem } from '#/api/system/llm';
import { createLlmApi, updateLlmApi } from '#/api/system/llm';
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
  const data = modalApi.getData<Partial<LlmItem>>();
  isEdit.value = !!data?.id;
  modalApi.setState({
    title: isEdit.value ? $t('llm.formTitleEdit') : $t('llm.formTitleAdd'),
  });

  const schema: VbenFormSchema[] = [
    {
      fieldName: 'name',
      label: $t('llm.name'),
      component: 'Input',
      componentProps: {
        placeholder: $t('llm.namePlaceholder'),
        maxlength: 64,
      },
      rules: z.string().min(1, { message: $t('llm.requiredHint') }),
    },
    {
      fieldName: 'baseUrl',
      label: $t('llm.baseUrl'),
      component: 'Input',
      componentProps: {
        placeholder: $t('llm.baseUrlPlaceholder'),
        maxlength: 255,
      },
      rules: z.string().min(1, { message: $t('llm.requiredHint') }),
    },
    {
      fieldName: 'apiKey',
      label: $t('llm.apiKey'),
      component: 'Input',
      componentProps: {
        type: 'password',
        showPassword: true,
        autocomplete: 'new-password',
        placeholder: isEdit.value
          ? $t('llm.apiKeyEditPlaceholder')
          : $t('llm.apiKeyPlaceholder'),
      },
    },
    {
      fieldName: 'model',
      label: $t('llm.model'),
      component: 'Input',
      componentProps: {
        placeholder: $t('llm.modelPlaceholder'),
        maxlength: 64,
      },
      rules: z.string().min(1, { message: $t('llm.requiredHint') }),
    },
    {
      fieldName: 'temperature',
      label: $t('llm.temperature'),
      component: 'InputNumber',
      componentProps: {
        min: 0,
        max: 2,
        step: 0.1,
        precision: 1,
        controlsPosition: 'right',
      },
    },
    {
      fieldName: 'maxTokens',
      label: $t('llm.maxTokens'),
      component: 'InputNumber',
      componentProps: {
        min: 1,
        step: 256,
        controlsPosition: 'right',
      },
    },
    {
      fieldName: 'timeoutSeconds',
      label: $t('llm.timeout'),
      component: 'Select',
      componentProps: {
        options: [30, 60, 120, 180].map((s) => ({ label: s, value: s })),
      },
      defaultValue: 60,
    },
    {
      fieldName: 'enabled',
      label: $t('llm.enabled'),
      component: 'RadioGroup',
      componentProps: {
        options: [
          { label: $t('llm.enabledOn'), value: 1 },
          { label: $t('llm.enabledOff'), value: 0 },
        ],
      },
      defaultValue: 1,
    },
    {
      fieldName: 'remark',
      label: $t('llm.remark'),
      component: 'Input',
      componentProps: {
        type: 'textarea',
        rows: 2,
        maxlength: 255,
        placeholder: $t('llm.remarkPlaceholder'),
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
      temperature: data.temperature ?? undefined,
      maxTokens: data.maxTokens ?? undefined,
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
      maxTokens: (values.maxTokens as number | undefined) ?? null,
      model: String(values.model ?? '').trim(),
      name: String(values.name ?? '').trim(),
      remark: (values.remark as string) || null,
      temperature: (values.temperature as number | undefined) ?? null,
      timeoutSeconds: (values.timeoutSeconds as number) || 60,
    };
    if (isEdit.value) {
      const id = (modalApi.getData() as { id: number }).id;
      await updateLlmApi(id, payload);
    } else {
      await createLlmApi(payload);
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
