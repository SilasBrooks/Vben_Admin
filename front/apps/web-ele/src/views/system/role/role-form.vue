<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { useVbenForm, z } from '#/adapter/form';
import {
  createRoleApi,
  getRoleDetailApi,
  updateRoleApi,
} from '#/api/system/role';

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
  const data = modalApi.getData<{ id?: number }>();
  isEdit.value = !!data?.id;
  modalApi.setState({
    title: isEdit.value ? '编辑角色' : '新增角色',
  });

  // 编辑时锁定 super 角色的 roleKey 不可修改
  let roleKeyDisabled = false;
  let roleKeyValue: string | undefined;

  if (isEdit.value && data?.id) {
    const detail = await getRoleDetailApi(data.id);
    roleKeyValue = detail.roleKey;
    roleKeyDisabled = detail.roleKey === 'super';
  }

  const schema: VbenFormSchema[] = [
    {
      fieldName: 'roleKey',
      label: '角色标识',
      component: 'Input',
      componentProps: {
        placeholder: '如 admin / user',
        disabled: roleKeyDisabled,
      },
      rules: z.string().min(1, { message: '请输入角色标识' }),
    },
    {
      fieldName: 'roleName',
      label: '角色名称',
      component: 'Input',
      componentProps: { placeholder: '如 管理员' },
      rules: z.string().min(1, { message: '请输入角色名称' }),
    },
    {
      fieldName: 'sortNum',
      label: '排序',
      component: 'InputNumber',
      defaultValue: 1,
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
  ];
  formApi.setState({ schema });

  await formApi.resetForm();
  if (isEdit.value && data?.id) {
    const detail = await getRoleDetailApi(data.id);
    await formApi.setValues(detail);
    if (roleKeyValue === 'super') {
      formApi.setFieldValue('roleKey', roleKeyValue);
    }
  }
}

async function handleSubmit() {
  const values = (await formApi.getValues()) as any;
  modalApi.lock();
  try {
    if (isEdit.value) {
      await updateRoleApi({ id: (modalApi.getData() as any).id, ...values });
    } else {
      await createRoleApi(values);
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
