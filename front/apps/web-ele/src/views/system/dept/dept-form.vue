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
    title: isEdit.value ? '编辑部门' : '新增部门',
  });

  deptTree.value = await getDeptTreeApi();
  // 父部门下拉树：编辑时排除自身及子孙；根部门用虚拟节点
  const candidates = excludeSubtree(deptTree.value, editId.value);
  const parentOptions = [{ id: 0, deptName: '根部门', children: candidates }];

  const schema: VbenFormSchema[] = [
    {
      fieldName: 'parentId',
      label: '父部门',
      component: 'TreeSelect',
      componentProps: {
        data: parentOptions,
        nodeKey: 'id',
        props: { label: 'deptName', children: 'children' },
        checkStrictly: true,
        defaultExpandAll: true,
        placeholder: '根部门 = 顶级',
      },
      defaultValue: 0,
    },
    {
      fieldName: 'deptName',
      label: '部门名称',
      component: 'Input',
      componentProps: { placeholder: '如 研发部' },
      rules: z.string().min(1, { message: '请输入部门名称' }),
    },
    {
      fieldName: 'leader',
      label: '负责人',
      component: 'Input',
      componentProps: { placeholder: '负责人姓名，可留空' },
    },
    {
      fieldName: 'orderNum',
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
      fieldName: 'remark',
      label: '备注',
      component: 'Input',
      componentProps: {
        type: 'textarea',
        placeholder: '备注信息，可留空',
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
