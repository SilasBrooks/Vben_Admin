<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';
import type { RoleItem } from '#/api/system/role';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { useVbenForm, z } from '#/adapter/form';
import type { DeptNode } from '#/api/system/dept';
import { getDeptTreeApi } from '#/api/system/dept';
import {
  createUserApi,
  getUserDetailApi,
  updateUserApi,
  type UserItem,
} from '#/api/system/user';
import { getRoleOptionsApi } from '#/api/system/role';
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
const roleOptions = ref<RoleItem[]>([]);
const deptTree = ref<DeptNode[]>([]);

// schema 在 init 时根据 isEdit 动态生成
const [Form, formApi] = useVbenForm({
  schema: [],
  showDefaultActions: false,
});

async function init() {
  const data = modalApi.getData<{ id?: number }>();
  isEdit.value = !!data?.id;
  modalApi.setState({
    title: isEdit.value ? $t('system.user.edit') : $t('system.user.add'),
  });

  // 加载角色选项（首次打开时缓存）
  if (roleOptions.value.length === 0) {
    roleOptions.value = await getRoleOptionsApi();
  }
  // 加载部门树（首次打开时缓存）
  if (deptTree.value.length === 0) {
    deptTree.value = await getDeptTreeApi();
  }

  // 重新生成 schema（让 isEdit 状态生效、角色 options 是最新值）
  const schema: VbenFormSchema[] = [
    {
      fieldName: 'username',
      label: $t('system.user.username'),
      component: 'Input',
      componentProps: {
        placeholder: $t('system.user.usernamePlaceholder'),
        disabled: isEdit.value,
      },
      rules: z.string().min(1, { message: $t('system.user.enterUsername') }),
    },
    {
      fieldName: 'password',
      label: $t('system.user.password'),
      component: 'Input',
      componentProps: {
        type: 'password',
        showPassword: true,
        placeholder: isEdit.value
          ? $t('system.user.passwordEditPlaceholder')
          : $t('system.user.passwordCreatePlaceholder'),
      },
      rules: isEdit.value
        ? z.string().optional()
        : z.string().min(6, { message: $t('system.user.passwordMinMessage') }),
    },
    {
      fieldName: 'nickname',
      label: $t('system.user.nickname'),
      component: 'Input',
      componentProps: { placeholder: $t('system.user.nicknamePlaceholder') },
      rules: z.string().min(1, { message: $t('system.user.enterNickname') }),
    },
    {
      fieldName: 'homePath',
      label: $t('system.user.homePath'),
      component: 'Input',
      componentProps: {
        placeholder: $t('system.user.homePathPlaceholder'),
      },
    },
    {
      fieldName: 'deptId',
      label: $t('system.user.dept'),
      component: 'TreeSelect',
      componentProps: {
        data: deptTree.value,
        nodeKey: 'id',
        props: { label: 'deptName', children: 'children' },
        checkStrictly: true,
        defaultExpandAll: true,
        clearable: true,
        placeholder: $t('system.user.deptPlaceholder'),
      },
    },
    {
      fieldName: 'roleIds',
      label: $t('system.user.roles'),
      component: 'Select',
      componentProps: {
        multiple: true,
        collapseTags: true,
        collapseTagsTooltip: true,
        options: roleOptions.value.map((r) => ({
          label: `${r.roleName}（${r.roleKey}）`,
          value: r.id,
        })),
        placeholder: $t('system.user.rolesPlaceholder'),
      },
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
