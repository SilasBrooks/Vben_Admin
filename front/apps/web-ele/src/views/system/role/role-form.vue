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
    title: isEdit.value ? '编辑角色' : '新增角色',
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
    {
      fieldName: 'dataScope',
      label: '数据范围',
      component: 'RadioGroup',
      componentProps: {
        options: [
          { label: '全部数据', value: '1' },
          { label: '自定义部门', value: '2' },
          { label: '本部门', value: '3' },
          { label: '本部门及以下', value: '4' },
          { label: '仅本人', value: '5' },
        ],
        // super 角色数据范围恒为全部数据（后端强制，前端锁定）
        disabled: roleKeyValue === 'super',
      },
      defaultValue: '5',
    },
    {
      fieldName: 'deptIds',
      label: '自定义部门',
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
        placeholder: '勾选可见部门（勾选父级时子孙部门自动包含）',
      },
      rules: z.array(z.number()).min(1, { message: '请至少选择一个部门' }),
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
    ElMessage.success('保存成功');
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
