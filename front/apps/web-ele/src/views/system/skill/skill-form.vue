<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { useVbenForm, z } from '#/adapter/form';
import type { SkillItem } from '#/api/system/skill';
import { createSkillApi, updateSkillApi } from '#/api/system/skill';
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
const isBuiltIn = ref(false);

const nameHint = computed(() =>
  isEdit.value ? '' : $t('skill.nameFormatHint'),
);

// schema 在 init 时根据 isEdit 动态生成
const [Form, formApi] = useVbenForm({
  schema: [],
  showDefaultActions: false,
});

async function init() {
  const data = modalApi.getData<Partial<SkillItem>>();
  isEdit.value = !!data?.id;
  isBuiltIn.value = data?.builtIn === 1;
  modalApi.setState({
    title: isEdit.value ? $t('skill.formTitleEdit') : $t('skill.formTitleAdd'),
  });

  const schema: VbenFormSchema[] = [
    {
      fieldName: 'name',
      label: $t('skill.name'),
      component: 'Input',
      componentProps: {
        placeholder: $t('skill.namePlaceholder'),
        maxlength: 64,
        disabled: isEdit.value,
      },
      help: nameHint.value,
      rules: z
        .string()
        .min(1, { message: $t('skill.requiredHint') })
        .regex(/^[a-z0-9][a-z0-9-]{1,63}$/, { message: $t('skill.nameFormatHint') }),
    },
    {
      fieldName: 'title',
      label: $t('skill.title'),
      component: 'Input',
      componentProps: {
        placeholder: $t('skill.titlePlaceholder'),
        maxlength: 64,
      },
      rules: z.string().min(1, { message: $t('skill.requiredHint') }),
    },
    {
      fieldName: 'description',
      label: $t('skill.description'),
      component: 'Input',
      componentProps: {
        type: 'textarea',
        rows: 3,
        maxlength: 300,
        showWordLimit: true,
        placeholder: $t('skill.descriptionPlaceholder'),
      },
      rules: z.string().min(1, { message: $t('skill.requiredHint') }),
    },
    {
      fieldName: 'content',
      label: $t('skill.content'),
      component: 'Input',
      componentProps: {
        type: 'textarea',
        rows: 14,
        maxlength: 8000,
        showWordLimit: true,
        placeholder: $t('skill.contentPlaceholder'),
      },
      rules: z.string().min(1, { message: $t('skill.requiredHint') }),
    },
  ];
  formApi.setState({ schema });

  await formApi.resetForm();
  if (isEdit.value && data) {
    await formApi.setValues({
      name: data.name,
      title: data.title,
      description: data.description,
      content: data.content,
    });
  }
}

async function handleSubmit() {
  const values = await formApi.getValues();
  modalApi.lock();
  try {
    const payload = {
      name: String(values.name ?? '').trim(),
      title: String(values.title ?? '').trim(),
      description: String(values.description ?? '').trim(),
      content: String(values.content ?? '').trim(),
    };
    if (isEdit.value) {
      const id = (modalApi.getData() as { id: number }).id;
      await updateSkillApi(id, payload);
    } else {
      await createSkillApi(payload);
    }
    emit('saved');
    modalApi.close();
  } finally {
    modalApi.unlock();
  }
}
</script>

<template>
  <Modal class="w-[760px]">
    <div v-if="isBuiltIn" class="text-foreground/60 mb-3 text-xs">
      {{ $t('skill.builtInHint') }}
    </div>
    <Form />
  </Modal>
</template>
