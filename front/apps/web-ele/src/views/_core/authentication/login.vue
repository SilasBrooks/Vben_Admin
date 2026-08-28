<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, ref } from 'vue';

import {
  AuthenticationLogin,
  SliderTranslateCaptcha,
  useVbenModal,
  z,
} from '@vben/common-ui';
import { $t } from '@vben/locales';

import { useAuthStore } from '#/store';

defineOptions({ name: 'Login' });

const authStore = useAuthStore();

// 拼图验证码弹窗：点登录 → 校验账号密码 → 弹拼图 → 通过后才真正调登录接口
const captchaPassed = ref(false);
const pendingLoginValues = ref<Record<string, any> | null>(null);

const [CaptchaModal, captchaModalApi] = useVbenModal({
  onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      // 关闭时重置通过标志，下次需重新验证
      captchaPassed.value = false;
    }
  },
  onConfirm: handleCaptchaConfirm,
});

function handleCaptchaConfirm() {
  if (!captchaPassed.value) {
    return;
  }
  captchaModalApi.lock();
  // 真正调登录接口
  authStore
    .authLogin(pendingLoginValues.value ?? {})
    .finally(() => {
      captchaModalApi.unlock();
      captchaModalApi.close();
      pendingLoginValues.value = null;
    });
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
 * 登录按钮提交：先校验表单，通过则弹出拼图验证码。
 * AuthenticationLogin 内部会先 formApi.validate()，valid 才 emit('submit', values)。
 */
function handleSubmit(values: Record<string, any>) {
  pendingLoginValues.value = values;
  captchaPassed.value = false;
  captchaModalApi.open();
}

function onCaptchaSuccess() {
  captchaPassed.value = true;
  // 自动触发确认（用户也可手动点弹窗的"确定"）
  handleCaptchaConfirm();
}
</script>

<template>
  <AuthenticationLogin
    :form-schema="formSchema"
    :loading="authStore.loginLoading"
    @submit="handleSubmit"
  />

  <CaptchaModal
    :title="$t('ui.captcha.sliderTranslateDefaultTip')"
    :show-cancel-button="true"
    :show-confirm-button="true"
    :confirm-disabled="!captchaPassed"
    class="w-[420px]"
  >
    <div class="py-2">
      <SliderTranslateCaptcha
        src="https://picsum.photos/seed/vben-captcha/420/280"
        :canvas-width="360"
        :canvas-height="220"
        :diff-distance="5"
        @success="onCaptchaSuccess"
      />
    </div>
  </CaptchaModal>
</template>
