<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';
import type { MenuNode } from '#/api/system/menu';
import type { RoleItem } from '#/api/system/role';

import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { useVbenForm, z } from '#/adapter/form';
import {
  createMenuApi,
  getMenuDetailApi,
  getMenuTreeApi,
  updateMenuApi,
} from '#/api/system/menu';
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
const menuTree = ref<MenuNode[]>([]);
const roleOptions = ref<RoleItem[]>([]);

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
  // 角色下拉选项（访问角色只能从已存在角色中选）
  if (roleOptions.value.length === 0) {
    roleOptions.value = await getRoleOptionsApi();
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
      componentProps: { placeholder: '全局唯一：目录/菜单=路由 name，按钮=按钮标识' },
      rules: z.string().min(1, { message: '请输入菜单标识' }),
    },
    {
      fieldName: 'title',
      label: '显示名称',
      component: 'Input',
      componentProps: { placeholder: '如 菜单管理' },
      rules: z.string().min(1, { message: '请输入显示名称' }),
    },
    // 图标：仅 M/C 显示（按钮不渲染菜单，无需图标）
    {
      fieldName: 'icon',
      label: '图标',
      component: 'Input',
      componentProps: { placeholder: '如 ant-design:menu-outlined' },
      dependencies: {
        triggerFields: ['menuType'],
        if: (values) => values.menuType !== 'F',
      },
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
      component: 'Select',
      defaultValue: [],
      componentProps: {
        multiple: true,
        collapseTags: true,
        collapseTagsTooltip: true,
        options: roleOptions.value.map((r) => ({
          label: `${r.roleName}（${r.roleKey}）`,
          value: r.roleKey,
        })),
        placeholder: '不选 = 当前登录用户的角色 + super',
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
    // 后端 authority 是逗号分隔字符串，回填为多选数组
    await formApi.setValues({
      ...detail,
      authority: detail.authority
        ? detail.authority.split(',').map((s) => s.trim()).filter(Boolean)
        : [],
    });
  } else if (data?.parentId) {
    await formApi.setValues({ parentId: data.parentId });
  }
}

async function handleSubmit() {
  const values = (await formApi.getValues()) as Partial<MenuNode> & {
    authority?: string[];
  };
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
  // 多选数组转回逗号分隔字符串（与后端 authority 存储格式一致）
  const authority = Array.isArray(values.authority)
    ? values.authority.join(',')
    : '';
  const payload: Partial<MenuNode> = { ...values, authority };
  modalApi.lock();
  try {
    if (isEdit.value) {
      await updateMenuApi({ id: (modalApi.getData() as any).id, ...payload });
    } else {
      await createMenuApi(payload);
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
