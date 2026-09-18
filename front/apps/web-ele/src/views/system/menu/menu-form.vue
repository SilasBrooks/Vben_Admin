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
    title: isEdit.value ? $t('system.menu.edit') : $t('system.menu.add'),
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
    { id: 0, title: $t('system.menu.rootMenu'), children: menuTree.value },
  ];

  const schema: VbenFormSchema[] = [
    {
      fieldName: 'menuType',
      label: $t('system.menu.menuType'),
      component: 'RadioGroup',
      componentProps: {
        options: [
          { label: $t('system.menu.typeDirectory'), value: 'M' },
          { label: $t('system.menu.typeMenu'), value: 'C' },
          { label: $t('system.menu.typeButton'), value: 'F' },
        ],
      },
      defaultValue: 'M',
    },
    {
      fieldName: 'parentId',
      label: $t('system.menu.parentMenu'),
      component: 'TreeSelect',
      componentProps: {
        data: parentOptions,
        nodeKey: 'id',
        props: { label: 'title', children: 'children' },
        checkStrictly: true,
        defaultExpandAll: true,
        placeholder: $t('system.menu.parentPlaceholder'),
      },
      defaultValue: 0,
    },
    {
      fieldName: 'menuName',
      label: $t('system.menu.menuName'),
      component: 'Input',
      componentProps: {
        placeholder: $t('system.menu.menuNamePlaceholder'),
      },
      rules: z.string().min(1, { message: $t('system.menu.enterMenuName') }),
    },
    {
      fieldName: 'title',
      label: $t('system.menu.displayName'),
      component: 'Input',
      componentProps: {
        placeholder: $t('system.menu.displayNamePlaceholder'),
      },
      rules: z.string().min(1, { message: $t('system.menu.enterDisplayName') }),
    },
    // 图标：仅 M/C 显示（按钮不渲染菜单，无需图标）
    {
      fieldName: 'icon',
      label: $t('system.menu.icon'),
      component: 'Input',
      componentProps: { placeholder: $t('system.menu.iconPlaceholder') },
      dependencies: {
        triggerFields: ['menuType'],
        if: (values) => values.menuType !== 'F',
      },
    },
    {
      fieldName: 'orderNum',
      label: $t('system.common.sort'),
      component: 'InputNumber',
      defaultValue: 1,
      componentProps: { min: 0 },
    },
    // 路由路径：M/C 显示
    {
      fieldName: 'path',
      label: $t('system.menu.routePath'),
      component: 'Input',
      componentProps: {
        placeholder: $t('system.menu.routePathPlaceholder'),
      },
      dependencies: {
        triggerFields: ['menuType'],
        if: (values) => values.menuType !== 'F',
      },
    },
    // 组件路径：仅 C 显示
    {
      fieldName: 'component',
      label: $t('system.menu.componentPath'),
      component: 'Input',
      componentProps: {
        placeholder: $t('system.menu.componentPathPlaceholder'),
      },
      dependencies: {
        triggerFields: ['menuType'],
        if: (values) => values.menuType === 'C',
      },
    },
    // 权限码：仅 F 显示
    {
      fieldName: 'perm',
      label: $t('system.menu.perm'),
      component: 'Input',
      componentProps: { placeholder: $t('system.menu.permPlaceholder') },
      dependencies: {
        triggerFields: ['menuType'],
        if: (values) => values.menuType === 'F',
      },
    },
    {
      fieldName: 'authority',
      label: $t('system.menu.authority'),
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
        placeholder: $t('system.menu.authorityPlaceholder'),
      },
    },
    {
      fieldName: 'visible',
      label: $t('system.menu.visible'),
      component: 'RadioGroup',
      componentProps: {
        options: [
          { label: $t('system.menu.show'), value: 0 },
          { label: $t('system.menu.hide'), value: 1 },
        ],
      },
      defaultValue: 0,
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
