<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, ref } from 'vue';

import { AuthenticationLogin, z } from '@vben/common-ui';
import { $t } from '@vben/locales';

import type { CaptchaVerification } from '#/api';
import { useAuthStore } from '#/store';
import CaptchaDialog from './captcha-dialog.vue';

// 不匹配认证布局的 Login 缓存白名单，离开时销毁密码与验证码状态。
defineOptions({ name: 'AccountLogin', inheritAttrs: false });

const authStore = useAuthStore();

// 服务端图形验证码弹窗：点登录 → 校验账号密码格式 → 弹出验证码 → 输码确认后才真正调登录接口
const pendingLoginValues = ref<Record<string, any> | null>(null);
const captchaDialog = ref<InstanceType<typeof CaptchaDialog>>();

async function handleCaptchaConfirm(captcha: CaptchaVerification) {
  if (!pendingLoginValues.value) return;
  try {
    await authStore.authLogin({ ...pendingLoginValues.value, ...captcha });
  } finally {
    pendingLoginValues.value = null;
  }
}

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      component: 'VbenInput',
      componentProps: {
        placeholder: $t('authentication.usernameTip'),
      },
      fieldName: 'username',
      label: $t('authentication.username'),
      rules: z.string().min(1, { message: $t('authentication.usernameTip') }),
    },
    {
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: $t('authentication.password'),
      },
      fieldName: 'password',
      label: $t('authentication.password'),
      rules: z.string().min(1, { message: $t('authentication.passwordTip') }),
    },
  ];
});

/**
 * 登录按钮提交：先校验表单，通过则弹出验证码弹窗。
 * AuthenticationLogin 内部会先 formApi.validate()，valid 才 emit('submit', values)。
 */
function handleSubmit(values: Record<string, any>) {
  pendingLoginValues.value = values;
  captchaDialog.value?.open();
}
</script>

<template>
  <div v-bind="$attrs">
    <AuthenticationLogin
      :sub-title="$t('account.brand.loginDescription')"
      :form-schema="formSchema"
      :loading="authStore.loginLoading"
      :show-register="false"
      :show-code-login="false"
      :show-qrcode-login="false"
      :show-third-party-login="false"
      @submit="handleSubmit"
    />

    <CaptchaDialog
      ref="captchaDialog"
      close-on-verify-error
      :verify="handleCaptchaConfirm"
    />
  </div>
</template>
