<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { ElMessage } from 'element-plus';

import { useVbenForm, z } from '#/adapter/form';
import type { DeptNode } from '#/api/system/dept';
import { getDeptTreeApi } from '#/api/system/dept';
import {
  createRoleApi,
  getDataScopeApi,
  getRoleDetailApi,
  updateDataScopeApi,
  updateRoleApi,
} from '#/api/system/role';
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
const deptTree = ref<DeptNode[]>([]);

const [Form, formApi] = useVbenForm({
  schema: [],
  showDefaultActions: false,
});

async function init() {
  const data = modalApi.getData<{ id?: number }>();
  isEdit.value = !!data?.id;
  modalApi.setState({
    title: isEdit.value ? $t('system.role.edit') : $t('system.role.add'),
  });

  // 编辑时锁定 super 角色的 roleKey 不可修改
  let roleKeyDisabled = false;
  let roleKeyValue: string | undefined;

  // 部门树（首次打开时缓存）
  if (deptTree.value.length === 0) {
    deptTree.value = await getDeptTreeApi();
  }

  let detail: Awaited<ReturnType<typeof getRoleDetailApi>> | undefined;
  let scope: { dataScope: string; deptIds: number[] } | undefined;
  if (isEdit.value && data?.id) {
    [detail, scope] = await Promise.all([
      getRoleDetailApi(data.id),
      getDataScopeApi(data.id),
    ]);
    roleKeyValue = detail.roleKey;
    roleKeyDisabled = detail.roleKey === 'super';
  }

  const schema: VbenFormSchema[] = [
    {
      fieldName: 'roleKey',
      label: $t('system.role.roleKey'),
      component: 'Input',
      componentProps: {
        placeholder: $t('system.role.roleKeyPlaceholder'),
        disabled: roleKeyDisabled,
      },
      rules: z.string().min(1, { message: $t('system.role.enterRoleKey') }),
    },
    {
      fieldName: 'roleName',
      label: $t('system.role.roleName'),
      component: 'Input',
      componentProps: { placeholder: $t('system.role.roleNamePlaceholder') },
      rules: z.string().min(1, { message: $t('system.role.enterRoleName') }),
    },
    {
      fieldName: 'sortNum',
      label: $t('system.common.sort'),
      component: 'InputNumber',
      defaultValue: 1,
      componentProps: { min: 0 },
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
      fieldName: 'dataScope',
      label: $t('system.role.dataScope'),
      component: 'RadioGroup',
      componentProps: {
        options: [
          { label: $t('system.role.scopeAll'), value: '1' },
          { label: $t('system.role.scopeCustom'), value: '2' },
          { label: $t('system.role.scopeDept'), value: '3' },
          { label: $t('system.role.scopeDeptAndBelow'), value: '4' },
          { label: $t('system.role.scopeSelf'), value: '5' },
        ],
        // super 角色数据范围恒为全部数据（后端强制，前端锁定）
        disabled: roleKeyValue === 'super',
      },
      defaultValue: '5',
    },
    {
      fieldName: 'deptIds',
      label: $t('system.role.scopeCustom'),
      component: 'TreeSelect',
      componentProps: {
        data: deptTree.value,
        nodeKey: 'id',
        props: { label: 'deptName', children: 'children' },
        multiple: true,
        showCheckbox: true,
        checkStrictly: true,
        defaultExpandAll: true,
        collapseTags: true,
        collapseTagsTooltip: true,
        placeholder: $t('system.role.deptIdsPlaceholder'),
      },
      rules: z
        .array(z.number())
        .min(1, { message: $t('system.role.selectDeptRequired') }),
      dependencies: {
        triggerFields: ['dataScope'],
        if: (values) => values.dataScope === '2',
      },
    },
  ];
  formApi.setState({ schema });

  await formApi.resetForm();
  if (detail) {
    await formApi.setValues({ ...detail, deptIds: scope?.deptIds ?? [] });
    if (roleKeyValue === 'super') {
      formApi.setFieldValue('roleKey', roleKeyValue);
      formApi.setFieldValue('dataScope', '1');
    }
  }
}

async function handleSubmit() {
  const values = (await formApi.getValues()) as any;
  const { dataScope, deptIds, ...roleValues } = values;
  modalApi.lock();
  try {
    let roleId: number;
    if (isEdit.value) {
      roleId = (modalApi.getData() as any).id;
      await updateRoleApi({ id: roleId, ...roleValues });
    } else {
      roleId = await createRoleApi(roleValues);
    }
    await updateDataScopeApi(roleId, dataScope, deptIds ?? []);
    ElMessage.success($t('system.common.saveSuccess'));
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
