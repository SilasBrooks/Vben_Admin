<script setup lang="ts">
import type { Recordable } from '@vben/types';

import type { VbenFormSchema } from '#/adapter/form';

import { computed, nextTick, onMounted, ref } from 'vue';

import { preferences } from '@vben/preferences';
import { useUserStore } from '@vben/stores';

import { ProfileBaseSetting } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { updateProfileApi } from '#/api/core/user';
import { uploadAvatarApi } from '#/api/system/file';
import { z } from '#/adapter/form';
import { useAuthStore } from '#/store';

const authStore = useAuthStore();
const userStore = useUserStore();

const profileBaseSettingRef = ref();
const fileInputRef = ref<HTMLInputElement>();
const uploading = ref(false);

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      fieldName: 'nickname',
      component: 'Input',
      label: '昵称',
      rules: z.string().min(1, { message: '请输入昵称' }),
    },
    {
      fieldName: 'roles',
      component: 'Input',
      componentProps: {
        disabled: true,
      },
      label: '角色',
    },
    {
      fieldName: 'introduction',
      component: 'Input',
      componentProps: {
        maxlength: 200,
        placeholder: '介绍一下自己（最多 200 字）',
        rows: 3,
        showWordLimit: true,
        type: 'textarea',
      },
      label: '个人简介',
    },
  ];
});

onMounted(async () => {
  // 先拉最新本人信息（防 store 残留旧快照），等 form 就绪后填值
  await authStore.fetchUserInfo();
  await nextTick();
  profileBaseSettingRef.value?.getFormApi().setValues({
    introduction: userStore.userInfo?.introduction ?? '',
    nickname: userStore.userInfo?.realName ?? '',
    roles: (userStore.userInfo?.roles ?? []).join(' / '),
  });
});

/** 提交本人资料修改，成功后刷新 store 使头部昵称同步 */
async function handleProfileSubmit(values: Recordable<any>) {
  await updateProfileApi(values.nickname, values.introduction ?? '');
  await authStore.fetchUserInfo();
  ElMessage.success('资料已更新');
}

function pickAvatar() {
  fileInputRef.value?.click();
}

/** 上传头像（仅图片 + 5MB 由后端校验），成功后刷新 store 使头部头像同步 */
async function onAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  // 清空 value，确保同一文件可重复选择
  input.value = '';
  if (!file) return;
  uploading.value = true;
  try {
    await uploadAvatarApi(file);
    await authStore.fetchUserInfo();
    ElMessage.success('头像已更新');
  } finally {
    uploading.value = false;
  }
}
</script>
<template>
  <div class="max-w-md">
    <div class="mb-6 flex items-center gap-4">
      <img
        :src="userStore.userInfo?.avatar ?? preferences.app.defaultAvatar"
        class="size-16 cursor-pointer rounded-full object-cover"
        alt="头像"
        @click="pickAvatar"
      />
      <div>
        <button
          class="border-border hover:border-primary hover:text-primary rounded-md border px-3 py-1 text-sm transition-colors disabled:cursor-not-allowed disabled:opacity-50"
          :disabled="uploading"
          type="button"
          @click="pickAvatar"
        >
          {{ uploading ? '上传中...' : '更换头像' }}
        </button>
        <p class="text-foreground/60 mt-1 text-xs">仅支持图片，最大 5MB</p>
      </div>
      <input
        ref="fileInputRef"
        type="file"
        accept="image/*"
        class="absolute size-px opacity-0"
        @change="onAvatarChange"
      />
    </div>
    <ProfileBaseSetting
      ref="profileBaseSettingRef"
      :form-schema="formSchema"
      @submit="handleProfileSubmit"
    />
  </div>
</template>
