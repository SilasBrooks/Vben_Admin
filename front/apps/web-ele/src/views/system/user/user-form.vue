<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';
import type { RoleItem } from '#/api/system/role';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { useVbenForm, z } from '#/adapter/form';
import {
  createUserApi,
  getUserDetailApi,
  updateUserApi,
  type UserItem,
} from '#/api/system/user';
import { getRoleOptionsApi } from '#/api/system/role';

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
const roleOptions = ref<RoleItem[]>([]);

// schema 在 init 时根据 isEdit 动态生成
const [Form, formApi] = useVbenForm({
  schema: [],
  showDefaultActions: false,
});

async function init() {
  const data = modalApi.getData<{ id?: number }>();
  isEdit.value = !!data?.id;
  modalApi.setState({
    title: isEdit.value ? '编辑用户' : '新增用户',
  });

  // 加载角色选项（首次打开时缓存）
  if (roleOptions.value.length === 0) {
    roleOptions.value = await getRoleOptionsApi();
  }

  // 重新生成 schema（让 isEdit 状态生效、角色 options 是最新值）
  const schema: VbenFormSchema[] = [
    {
      fieldName: 'username',
      label: '用户名',
      component: 'Input',
      componentProps: {
        placeholder: '登录用户名',
        disabled: isEdit.value,
      },
      rules: z.string().min(1, { message: '请输入用户名' }),
    },
    {
      fieldName: 'password',
      label: '密码',
      component: 'Input',
      componentProps: {
        type: 'password',
        showPassword: true,
        placeholder: isEdit.value ? '留空不修改' : '至少 6 位',
      },
      rules: isEdit.value
        ? z.string().optional()
        : z.string().min(6, { message: '密码至少 6 位' }),
    },
    {
      fieldName: 'nickname',
      label: '昵称',
      component: 'Input',
      componentProps: { placeholder: '显示名' },
      rules: z.string().min(1, { message: '请输入昵称' }),
    },
    {
      fieldName: 'homePath',
      label: '首页路径',
      component: 'Input',
      componentProps: { placeholder: '如 /workspace，可留空' },
    },
    {
      fieldName: 'roleIds',
      label: '角色',
      component: 'Select',
      componentProps: {
        multiple: true,
        collapseTags: true,
        collapseTagsTooltip: true,
        options: roleOptions.value.map((r) => ({
          label: `${r.roleName}（${r.roleKey}）`,
          value: r.id,
        })),
        placeholder: '选择角色（可多选）',
      },
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
    const detail = await getUserDetailApi(data.id);
    await formApi.setValues({
      ...detail,
      password: '',
    });
  }
}

async function handleSubmit() {
  const values = (await formApi.getValues()) as Partial<UserItem>;
  modalApi.lock();
  try {
    if (isEdit.value) {
      const payload = { ...values };
      delete (payload as any).password;
      await updateUserApi({
        id: (modalApi.getData() as any).id,
        ...payload,
      });
    } else {
      await createUserApi(values);
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
