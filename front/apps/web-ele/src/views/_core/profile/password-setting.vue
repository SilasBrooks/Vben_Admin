<script setup lang="ts">
import type { Recordable } from '@vben/types';

import type { VbenFormSchema } from '#/adapter/form';

import { computed } from 'vue';

import { ProfilePasswordSetting, z } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { changePasswordApi } from '#/api/core/auth';
import { $t } from '#/locales';
import { useAuthStore } from '#/store';

const authStore = useAuthStore();

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      fieldName: 'oldPassword',
      label: $t('profile.password.oldPassword'),
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: $t('profile.password.oldPasswordPlaceholder'),
      },
    },
    {
      fieldName: 'newPassword',
      label: $t('profile.password.newPassword'),
      component: 'VbenInputPassword',
      componentProps: {
        passwordStrength: true,
        placeholder: $t('profile.password.newPasswordPlaceholder'),
      },
    },
    {
      fieldName: 'confirmPassword',
      label: $t('profile.password.confirmPassword'),
      component: 'VbenInputPassword',
      componentProps: {
        passwordStrength: true,
        placeholder: $t('profile.password.confirmAgain'),
      },
      dependencies: {
        rules(values) {
          const { newPassword } = values;
          return z
            .string({ required_error: $t('profile.password.confirmAgain') })
            .min(1, { message: $t('profile.password.confirmAgain') })
            .refine((value) => value === newPassword, {
              message: $t('profile.password.passwordMismatch'),
            });
        },
        triggerFields: ['newPassword'],
      },
    },
  ];
});

/** 改密成功后 token 全部失效，主动登出（logout 内部跳转登录页） */
async function handleSubmit(values: Recordable<any>) {
  await changePasswordApi(values.oldPassword, values.newPassword);
  ElMessage.success($t('profile.password.changed'));
  await authStore.logout(false);
}
</script>
<template>
  <ProfilePasswordSetting
    class="w-1/3"
    :form-schema="formSchema"
    @submit="handleSubmit"
  />
</template>
