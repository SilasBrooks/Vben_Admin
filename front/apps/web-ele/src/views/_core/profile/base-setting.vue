<script setup lang="ts">
import type { UploadRequestOptions } from 'element-plus';

import type { Recordable } from '@vben/types';

import type { VbenFormSchema } from '#/adapter/form';

import { computed, nextTick, onMounted, ref } from 'vue';

import { preferences } from '@vben/preferences';
import { useUserStore } from '@vben/stores';

import { ProfileBaseSetting } from '@vben/common-ui';

import { ElMessage, ElUpload } from 'element-plus';

import { updateProfileApi } from '#/api/core/user';
import { uploadAvatarApi } from '#/api/system/file';
import { $t } from '#/locales';
import { z } from '#/adapter/form';
import { useAuthStore } from '#/store';

const authStore = useAuthStore();
const userStore = useUserStore();

const profileBaseSettingRef = ref();
const uploading = ref(false);

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      fieldName: 'nickname',
      component: 'Input',
      label: $t('profile.base.nickname'),
      rules: z.string().min(1, { message: $t('profile.base.nicknameRequired') }),
    },
    {
      fieldName: 'roles',
      component: 'Input',
      componentProps: {
        disabled: true,
      },
      label: $t('profile.base.role'),
    },
    {
      fieldName: 'email',
      component: 'Input',
      componentProps: {
        maxlength: 255,
        placeholder: $t('profile.base.emailPlaceholder'),
      },
      label: $t('profile.base.email'),
      rules: z.string().email({ message: $t('profile.base.emailInvalid') }).or(z.literal('')),
    },
    {
      fieldName: 'introduction',
      component: 'Input',
      componentProps: {
        maxlength: 200,
        placeholder: $t('profile.base.introductionPlaceholder'),
        rows: 3,
        showWordLimit: true,
        type: 'textarea',
      },
      label: $t('profile.base.introduction'),
    },
  ];
});

onMounted(async () => {
  // 先拉最新本人信息（防 store 残留旧快照），等 form 就绪后填值
  await authStore.fetchUserInfo();
  await nextTick();
  profileBaseSettingRef.value?.getFormApi().setValues({
    email: userStore.userInfo?.email ?? '',
    introduction: userStore.userInfo?.introduction ?? '',
    nickname: userStore.userInfo?.realName ?? '',
    roles: (userStore.userInfo?.roles ?? []).join(' / '),
  });
});

/** 提交本人资料修改，成功后刷新 store 使头部昵称同步 */
async function handleProfileSubmit(values: Recordable<any>) {
  await updateProfileApi(values.nickname, values.introduction ?? '', values.email ?? '');
  await authStore.fetchUserInfo();
  ElMessage.success($t('profile.base.profileUpdated'));
}

/** ElUpload 自定义上传：仅图片 + 5MB 由后端校验，成功后刷新 store 使头部头像同步 */
async function onAvatarUpload(options: UploadRequestOptions) {
  uploading.value = true;
  try {
    await uploadAvatarApi(options.file as File);
    await authStore.fetchUserInfo();
    ElMessage.success($t('profile.base.avatarUpdated'));
  } finally {
    uploading.value = false;
  }
}
</script>
<template>
  <div class="max-w-md">
    <!-- ElUpload 托管文件选择：点头像或按钮均可靠弹出选择框，accept 限定图片 -->
    <ElUpload
      accept="image/*"
      :disabled="uploading"
      :http-request="onAvatarUpload"
      :show-file-list="false"
    >
      <div class="flex items-center gap-4">
        <img
          :src="userStore.userInfo?.avatar ?? preferences.app.defaultAvatar"
          :alt="$t('profile.base.avatar')"
          class="size-16 cursor-pointer rounded-full object-cover"
        />
        <div>
          <button
            class="border-border hover:border-primary hover:text-primary rounded-md border px-3 py-1 text-sm transition-colors disabled:cursor-not-allowed disabled:opacity-50"
            :disabled="uploading"
            type="button"
          >
            {{ uploading ? $t('profile.base.uploading') : $t('profile.base.changeAvatar') }}
          </button>
          <p class="text-foreground/60 mt-1 text-xs">{{ $t('profile.base.avatarTip') }}</p>
        </div>
      </div>
    </ElUpload>
    <ProfileBaseSetting
      ref="profileBaseSettingRef"
      :form-schema="formSchema"
      @submit="handleProfileSubmit"
    />
  </div>
</template>
