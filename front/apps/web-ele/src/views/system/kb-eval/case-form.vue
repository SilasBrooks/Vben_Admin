<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm, z } from '#/adapter/form';
import type { KbDocItem } from '#/api/system/kb';
import { getKbDocListApi } from '#/api/system/kb';
import type { KbEvalCaseItem } from '#/api/system/kbEval';
import { createKbEvalCaseApi, updateKbEvalCaseApi } from '#/api/system/kbEval';
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
/** 期望文档下拉选项（知识库文档列表，打开时加载一次） */
const docOptions = ref<{ label: string; value: number }[]>([]);

const [Form, formApi] = useVbenForm({
  schema: [],
  showDefaultActions: false,
});

async function init() {
  const data = modalApi.getData<Partial<KbEvalCaseItem>>();
  isEdit.value = !!data?.id;
  modalApi.setState({
    title: isEdit.value
      ? $t('kbEval.caseFormTitleEdit')
      : $t('kbEval.caseFormTitleAdd'),
  });

  // 期望文档下拉：取知识库文档列表（管理页低频，单次拉取足够）
  try {
    const page = await getKbDocListApi({ pageNo: 1, pageSize: 500 });
    docOptions.value = page.items.map((doc: KbDocItem) => ({
      label: doc.fileName,
      value: doc.id,
    }));
  } catch {
    docOptions.value = [];
  }

  const schema: VbenFormSchema[] = [
    {
      fieldName: 'query',
      label: $t('kbEval.queryLabel'),
      component: 'Input',
      componentProps: {
        type: 'textarea',
        rows: 3,
        maxlength: 512,
        placeholder: $t('kbEval.queryPlaceholder'),
      },
      rules: z.string().min(1, { message: $t('kbEval.queryRequiredHint') }),
    },
    {
      fieldName: 'expectDocId',
      label: $t('kbEval.expectDocLabel'),
      component: 'Select',
      componentProps: {
        options: docOptions.value,
        filterable: true,
        placeholder: $t('kbEval.expectDocPlaceholder'),
      },
      rules: 'selectRequired',
    },
    {
      fieldName: 'expectSeq',
      label: $t('kbEval.expectSeqLabel'),
      component: 'InputNumber',
      componentProps: {
        min: 0,
        precision: 0,
        placeholder: $t('kbEval.expectSeqPlaceholder'),
      },
      help: $t('kbEval.expectSeqHelp'),
    },
    {
      fieldName: 'note',
      label: $t('kbEval.noteLabel'),
      component: 'Input',
      componentProps: {
        type: 'textarea',
        rows: 2,
        maxlength: 255,
        placeholder: $t('kbEval.notePlaceholder'),
      },
    },
  ];
  formApi.setState({ schema });

  await formApi.resetForm();
  if (isEdit.value && data) {
    await formApi.setValues({
      expectDocId: data.expectDocId,
      expectSeq: data.expectSeq ?? undefined,
      note: data.note ?? '',
      query: data.query,
    });
  }
}

async function handleSubmit() {
  const values = await formApi.getValues();
  modalApi.lock();
  try {
    const payload = {
      expectDocId: values.expectDocId as number,
      expectSeq:
        values.expectSeq === undefined || values.expectSeq === null
          ? null
          : Number(values.expectSeq),
      note: (values.note as string) || undefined,
      query: String(values.query ?? '').trim(),
    };
    if (isEdit.value) {
      const id = (modalApi.getData() as { id: number }).id;
      await updateKbEvalCaseApi(id, payload);
    } else {
      const setId = (modalApi.getData() as { setId: number }).setId;
      await createKbEvalCaseApi(setId, payload);
    }
    ElMessage.success($t('kbEval.caseSaveSuccess'));
    emit('saved');
    modalApi.close();
  } finally {
    modalApi.unlock();
  }
}
</script>

<template>
  <Modal class="w-[560px]">
    <Form />
  </Modal>
</template>
