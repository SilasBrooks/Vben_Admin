<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';
import type { DeptNode } from '#/api/system/dept';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { useVbenForm, z } from '#/adapter/form';
import {
  createDeptApi,
  getDeptDetailApi,
  getDeptTreeApi,
  updateDeptApi,
} from '#/api/system/dept';
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
const editId = ref<number>();
const deptTree = ref<DeptNode[]>([]);

const [Form, formApi] = useVbenForm({
  schema: [],
  showDefaultActions: false,
});

/** 从树中剔除 deptId 及其所有子孙（编辑时父部门候选排除自身及子孙，防环） */
function excludeSubtree(nodes: DeptNode[], excludeId?: number): DeptNode[] {
  if (!excludeId) {
    return nodes;
  }
  return nodes
    .filter((n) => n.id !== excludeId)
    .map((n) => ({
      ...n,
      children: n.children ? excludeSubtree(n.children, excludeId) : undefined,
    }));
}

async function init() {
  const data = modalApi.getData<{ id?: number; parentId?: number }>();
  isEdit.value = !!data?.id;
  editId.value = data?.id;
  modalApi.setState({
    title: isEdit.value ? $t('system.dept.edit') : $t('system.dept.add'),
  });

  deptTree.value = await getDeptTreeApi();
  // 父部门下拉树：编辑时排除自身及子孙；根部门用虚拟节点
  const candidates = excludeSubtree(deptTree.value, editId.value);
  const parentOptions = [{ id: 0, deptName: $t('system.dept.rootDept'), children: candidates }];

  const schema: VbenFormSchema[] = [
    {
      fieldName: 'parentId',
      label: $t('system.dept.parentDept'),
      component: 'TreeSelect',
      componentProps: {
        data: parentOptions,
        nodeKey: 'id',
        props: { label: 'deptName', children: 'children' },
        checkStrictly: true,
        defaultExpandAll: true,
        placeholder: $t('system.dept.parentPlaceholder'),
      },
      defaultValue: 0,
    },
    {
      fieldName: 'deptName',
      label: $t('system.dept.deptName'),
      component: 'Input',
      componentProps: {
        placeholder: $t('system.dept.deptNamePlaceholder'),
      },
      rules: z.string().min(1, { message: $t('system.dept.enterDeptName') }),
    },
    {
      fieldName: 'leader',
      label: $t('system.dept.leader'),
      component: 'Input',
      componentProps: {
        placeholder: $t('system.dept.leaderPlaceholder'),
      },
    },
    {
      fieldName: 'orderNum',
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
      fieldName: 'remark',
      label: $t('system.common.remark'),
      component: 'Input',
      componentProps: {
        type: 'textarea',
        placeholder: $t('system.dept.remarkPlaceholder'),
        rows: 2,
      },
    },
  ];
  formApi.setState({ schema });

  await formApi.resetForm();
  if (isEdit.value && data?.id) {
    const detail = await getDeptDetailApi(data.id);
    await formApi.setValues(detail);
  } else if (data?.parentId) {
    await formApi.setValues({ parentId: data.parentId });
  }
}

async function handleSubmit() {
  const values = (await formApi.getValues()) as Partial<DeptNode>;
  modalApi.lock();
  try {
    if (isEdit.value) {
      await updateDeptApi({ id: editId.value, ...values });
    } else {
      await createDeptApi(values);
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
