<script lang="ts" setup>
import type { MenuNode } from '#/api/system/menu';

import { ref } from 'vue';

import { Tree, useVbenModal } from '@vben/common-ui';
import { ElMessage } from 'element-plus';

import { assignRoleMenusApi, getRoleMenuIdsApi } from '#/api/system/role';
import { getMenuTreeApi } from '#/api/system/menu';
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

const menuTree = ref<MenuNode[]>([]);
// Tree 组件通过 v-model 双向同步（defineModel），不能用 default-value + @update:value
const selectedIds = ref<number[]>([]);
const roleId = ref<number>();

/** 菜单标题存的是 i18n key（F 型权限码为明文，$t 缺 key 时原样透传），树展示前统一翻译 */
function translateTitles(nodes: MenuNode[]): MenuNode[] {
  return nodes.map((node) => ({
    ...node,
    title: $t(node.title),
    children: node.children ? translateTitles(node.children) : undefined,
  }));
}

async function init() {
  const data = modalApi.getData<{ id?: number }>();
  roleId.value = data?.id;
  modalApi.setState({ title: $t('system.role.assignMenus') });

  // 先清空选中状态，避免上一次的选中残留
  selectedIds.value = [];

  // 并行加载菜单树和当前角色已分配的菜单 id
  const [tree, ids] = await Promise.all([
    getMenuTreeApi(),
    roleId.value ? getRoleMenuIdsApi(roleId.value) : Promise.resolve([]),
  ]);
  menuTree.value = translateTitles(tree);
  // 重新赋值（新数组引用，触发 v-model 响应式更新 → Tree 内部 watchEffect 重算 treeValue）
  selectedIds.value = [...ids];
}

async function handleSubmit() {
  if (!roleId.value) {
    modalApi.close();
    return;
  }
  modalApi.lock();
  try {
    await assignRoleMenusApi(roleId.value, selectedIds.value);
    ElMessage.success($t('system.role.authSuccess'));
    emit('saved');
    modalApi.close();
  } finally {
    modalApi.unlock();
  }
}
</script>

<template>
  <Modal>
    <Tree
      v-model="selectedIds"
      :tree-data="menuTree as any"
      label-field="title"
      value-field="id"
      children-field="children"
      multiple
      bordered
      transition
    />
  </Modal>
</template>
