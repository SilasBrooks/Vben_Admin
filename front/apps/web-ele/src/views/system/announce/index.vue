<script lang="ts" setup>
import type { AnnounceTargetType } from '#/api/notice';
import type { DeptNode } from '#/api/system/dept';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTreeSelect,
} from 'element-plus';

import { announceApi } from '#/api/notice';
import { getDeptTreeApi } from '#/api/system/dept';
import { getUserListApi } from '#/api/system/user';
import { $t } from '#/locales';

defineOptions({ name: 'SystemAnnounce' });

const formRef = ref();
const submitting = ref(false);

const form = reactive({
  content: '',
  deptIds: [] as number[],
  targetType: 'all' as AnnounceTargetType,
  title: '',
  userIds: [] as number[],
});

const rules = {
  content: [{ max: 500, message: $t('announce.contentMax'), trigger: 'blur' }],
  title: [
    { required: true, message: $t('announce.titleRequired'), trigger: 'blur' },
    { max: 100, message: $t('announce.titleMax'), trigger: 'blur' },
  ],
};

/** 部门树数据（发布范围=按部门时展示） */
const deptTree = ref<DeptNode[]>([]);
/** 用户选项（发布范围=按用户时展示） */
const userOptions = ref<{ label: string; value: number }[]>([]);

onMounted(async () => {
  try {
    const [tree, users] = await Promise.all([
      getDeptTreeApi(),
      getUserListApi({ pageNo: 1, pageSize: 100 }),
    ]);
    deptTree.value = tree;
    userOptions.value = users.items.map((u) => ({
      label: u.nickname ? `${u.nickname}（${u.username}）` : u.username,
      value: u.id,
    }));
  } catch {
    // 部门/用户接口权限不足时静默降级：仍可按全员发布
  }
});

/** 切换目标范围时清空另一维度的选择，避免提交脏数据 */
function onTargetChange() {
  form.deptIds = [];
  form.userIds = [];
}

async function handleSubmit() {
  await formRef.value?.validate();
  if (form.targetType === 'dept' && form.deptIds.length === 0) {
    ElMessage.warning($t('announce.selectDeptFirst'));
    return;
  }
  if (form.targetType === 'user' && form.userIds.length === 0) {
    ElMessage.warning($t('announce.selectUserFirst'));
    return;
  }
  submitting.value = true;
  try {
    const res = await announceApi({
      content: form.content || undefined,
      deptIds: form.targetType === 'dept' ? form.deptIds : undefined,
      targetType: form.targetType,
      title: form.title,
      userIds: form.targetType === 'user' ? form.userIds : undefined,
    });
    ElMessage.success($t('announce.success').replace('{count}', String(res.count)));
    formRef.value?.resetFields();
    form.deptIds = [];
    form.userIds = [];
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <Page :title="$t('announce.title')" auto-content-height>
    <ElCard class="mx-auto mt-2 max-w-[720px]">
      <ElForm ref="formRef" :model="form" :rules="rules" label-width="100px">
        <ElFormItem :label="$t('announce.titleField')" prop="title">
          <ElInput
            v-model="form.title"
            :maxlength="100"
            :placeholder="$t('announce.titlePlaceholder')"
            show-word-limit
          />
        </ElFormItem>
        <ElFormItem :label="$t('announce.contentField')" prop="content">
          <ElInput
            v-model="form.content"
            :maxlength="500"
            :placeholder="$t('announce.contentPlaceholder')"
            :rows="5"
            show-word-limit
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem :label="$t('announce.targetType')">
          <ElRadioGroup v-model="form.targetType" @change="onTargetChange">
            <ElRadioButton value="all">{{ $t('announce.targetAll') }}</ElRadioButton>
            <ElRadioButton value="dept">{{ $t('announce.targetDept') }}</ElRadioButton>
            <ElRadioButton value="user">{{ $t('announce.targetUser') }}</ElRadioButton>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem
          v-if="form.targetType === 'dept'"
          :label="$t('announce.selectDeptLabel')"
          prop="deptIds"
        >
          <ElTreeSelect
            v-model="form.deptIds"
            check-strictly
            class="w-full"
            :data="deptTree"
            multiple
            node-key="id"
            :placeholder="$t('announce.selectDept')"
            :props="{ label: 'deptName', children: 'children' }"
            show-checkbox
          />
        </ElFormItem>
        <ElFormItem
          v-if="form.targetType === 'user'"
          :label="$t('announce.selectUserLabel')"
          prop="userIds"
        >
          <ElSelect
            v-model="form.userIds"
            class="w-full"
            filterable
            multiple
            :placeholder="$t('announce.selectUser')"
          >
            <ElOption
              v-for="u in userOptions"
              :key="u.value"
              :label="u.label"
              :value="u.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton
            v-access:code="'Notice:Announce:Publish'"
            :loading="submitting"
            type="primary"
            @click="handleSubmit"
          >
            {{ $t('announce.submit') }}
          </ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>
  </Page>
</template>
