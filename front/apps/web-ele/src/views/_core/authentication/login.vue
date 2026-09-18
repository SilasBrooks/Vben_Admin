<script lang="ts" setup>
import type { VbenFormSchema } from '@vben/common-ui';

import { computed, ref } from 'vue';

import { AuthenticationLogin, useVbenModal, z } from '@vben/common-ui';
import { ElInput } from 'element-plus';
import { $t } from '@vben/locales';

import { getCaptchaApi } from '#/api';
import { useAuthStore } from '#/store';

defineOptions({ name: 'Login', inheritAttrs: false });

const authStore = useAuthStore();

// 服务端图形验证码弹窗：点登录 → 校验账号密码格式 → 弹出验证码 → 输码确认后才真正调登录接口
const pendingLoginValues = ref<Record<string, any> | null>(null);
const captchaImage = ref('');
const captchaCode = ref('');
const captchaId = ref('');
const captchaLoading = ref(false);

const [CaptchaModal, captchaModalApi] = useVbenModal({
  onOpenChange(isOpen: boolean) {
    if (isOpen) {
      // 每次弹窗打开都取新验证码
      refreshCaptcha();
    } else {
      captchaCode.value = '';
      captchaId.value = '';
      captchaImage.value = '';
    }
  },
  onConfirm: handleCaptchaConfirm,
});

async function refreshCaptcha() {
  captchaLoading.value = true;
  try {
    const res = await getCaptchaApi();
    captchaId.value = res.captchaId;
    captchaImage.value = res.image;
    captchaCode.value = '';
  } finally {
    captchaLoading.value = false;
  }
}

function handleCaptchaConfirm() {
  if (!captchaCode.value) {
    return;
  }
  captchaModalApi.lock();
  // 真正调登录接口：携带验证码；失败（如验证码错）保持弹窗并换码重试
  authStore
    .authLogin({
      ...(pendingLoginValues.value ?? {}),
      captchaId: captchaId.value,
      captchaCode: captchaCode.value,
    })
    .then(() => {
      captchaModalApi.close();
      pendingLoginValues.value = null;
    })
    .catch(() => refreshCaptcha())
    .finally(() => captchaModalApi.unlock());
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
  captchaModalApi.open();
}
</script>

<template>
  <!-- 单根包裹：修复 Transition 动画警告；$attrs（外部 class/data-side）原样透传给登录表单 -->
  <div>
    <AuthenticationLogin
      v-bind="$attrs"
      :form-schema="formSchema"
      :loading="authStore.loginLoading"
      @submit="handleSubmit"
    />

    <CaptchaModal
      :title="$t('profile.login.captchaTitle')"
      :show-cancel-button="true"
      :show-confirm-button="true"
      :confirm-disabled="!captchaCode"
      class="w-[400px]"
    >
      <div class="py-2">
        <!-- 点击图片刷新验证码 -->
        <div class="cursor-pointer" :title="$t('profile.login.refreshCaptcha')" @click="refreshCaptcha">
          <img
            v-if="captchaImage && !captchaLoading"
            :src="captchaImage"
            :alt="$t('profile.login.captcha')"
            class="h-[52px] w-full rounded-md border"
          />
          <div
            v-else
            class="flex h-[52px] w-full items-center justify-center rounded-md border bg-muted text-xs text-muted-foreground"
          >
            {{ $t('profile.login.captchaLoading') }}
          </div>
        </div>

        <ElInput
          v-model="captchaCode"
          class="mt-3"
          :placeholder="$t('profile.login.captchaPlaceholder')"
          @keyup.enter="handleCaptchaConfirm"
        />
      </div>
    </CaptchaModal>
  </div>
</template>
