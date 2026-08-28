<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';
import type { MenuNode } from '#/api/system/menu';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { useVbenForm, z } from '#/adapter/form';
import {
  createMenuApi,
  getMenuDetailApi,
  getMenuTreeApi,
  updateMenuApi,
} from '#/api/system/menu';

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
const menuTree = ref<MenuNode[]>([]);

const [Form, formApi] = useVbenForm({
  schema: [],
  showDefaultActions: false,
});

async function init() {
  const data = modalApi.getData<{ id?: number; parentId?: number }>();
  isEdit.value = !!data?.id;
  modalApi.setState({
    title: isEdit.value ? '编辑菜单' : '新增菜单',
  });

  if (menuTree.value.length === 0) {
    menuTree.value = await getMenuTreeApi();
  }
  // 父级菜单下拉树：套一个虚拟根节点
  const parentOptions = [
    { id: 0, title: '根菜单', children: menuTree.value },
  ];

  const schema: VbenFormSchema[] = [
    {
      fieldName: 'menuType',
      label: '菜单类型',
      component: 'RadioGroup',
      componentProps: {
        options: [
          { label: '目录', value: 'M' },
          { label: '菜单', value: 'C' },
          { label: '按钮', value: 'F' },
        ],
      },
      defaultValue: 'M',
    },
    {
      fieldName: 'parentId',
      label: '父级菜单',
      component: 'TreeSelect',
      componentProps: {
        data: parentOptions,
        nodeKey: 'id',
        props: { label: 'title', children: 'children' },
        checkStrictly: true,
        defaultExpandAll: true,
        placeholder: '根菜单 = 顶级',
      },
      defaultValue: 0,
    },
    {
      fieldName: 'menuName',
      label: '菜单标识',
      component: 'Input',
      componentProps: { placeholder: '路由 name，全局唯一' },
      rules: z.string().min(1, { message: '请输入菜单标识' }),
    },
    {
      fieldName: 'title',
      label: '显示名称',
      component: 'Input',
      componentProps: { placeholder: '如 菜单管理' },
      rules: z.string().min(1, { message: '请输入显示名称' }),
    },
    {
      fieldName: 'icon',
      label: '图标',
      component: 'Input',
      componentProps: { placeholder: '如 ant-design:menu-outlined' },
    },
    {
      fieldName: 'orderNum',
      label: '排序',
      component: 'InputNumber',
      defaultValue: 1,
      componentProps: { min: 0 },
    },
    // 路由路径：M/C 显示
    {
      fieldName: 'path',
      label: '路由路径',
      component: 'Input',
      componentProps: { placeholder: '如 /system/menu' },
      dependencies: {
        triggerFields: ['menuType'],
        if: (values) => values.menuType !== 'F',
      },
    },
    // 组件路径：仅 C 显示
    {
      fieldName: 'component',
      label: '组件路径',
      component: 'Input',
      componentProps: { placeholder: '如 /system/menu/index' },
      dependencies: {
        triggerFields: ['menuType'],
        if: (values) => values.menuType === 'C',
      },
    },
    // 权限码：仅 F 显示
    {
      fieldName: 'perm',
      label: '权限码',
      component: 'Input',
      componentProps: { placeholder: '如 System:Menu:Add' },
      dependencies: {
        triggerFields: ['menuType'],
        if: (values) => values.menuType === 'F',
      },
    },
    {
      fieldName: 'authority',
      label: '访问角色',
      component: 'Input',
      componentProps: {
        placeholder: '逗号分隔，留空 = 当前用户角色 + super',
      },
    },
    {
      fieldName: 'visible',
      label: '显示状态',
      component: 'RadioGroup',
      componentProps: {
        options: [
          { label: '显示', value: 0 },
          { label: '隐藏', value: 1 },
        ],
      },
      defaultValue: 0,
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
    const detail = await getMenuDetailApi(data.id);
    await formApi.setValues(detail);
  } else if (data?.parentId) {
    await formApi.setValues({ parentId: data.parentId });
  }
}

async function handleSubmit() {
  const values = (await formApi.getValues()) as Partial<MenuNode>;
  // 类型校验：C 必填 component；F 必填 perm；M/C 必填 path
  if (values.menuType === 'C' && !values.component?.trim()) {
    return;
  }
  if (values.menuType === 'F' && !values.perm?.trim()) {
    return;
  }
  if (values.menuType !== 'F' && !values.path?.trim()) {
    return;
  }
  modalApi.lock();
  try {
    if (isEdit.value) {
      await updateMenuApi({ id: (modalApi.getData() as any).id, ...values });
    } else {
      await createMenuApi(values);
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
