<script setup lang="ts">
import type { VbenFormSchema } from '@vben/common-ui';
import type { CaptchaVerification } from '#/api';
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
} from 'vue';
import { useRouter } from 'vue-router';
import { useVbenForm, VbenButton, z } from '@vben/common-ui';
import { LOGIN_PATH } from '@vben/constants';
import {
  getRecoveryOptionsApi,
  resetForgottenPasswordApi,
  sendRecoveryCodeApi,
} from '#/api';
import { useEmailCodeCountdown } from '#/hooks/use-email-code-countdown';
import { $t } from '#/locales';
import CaptchaDialog from './captcha-dialog.vue';

defineOptions({ name: 'AccountRecovery', inheritAttrs: false });
const router = useRouter();
const enabled = ref(false);
const loaded = ref(false);
const loadFailed = ref(false);
const busy = ref(false);
const step = ref<'account' | 'done' | 'reset'>('account');
const captchaDialog = ref<InstanceType<typeof CaptchaDialog>>();
const { remaining, start } = useEmailCodeCountdown();
let username = '';
let challengeId = '';
let disposed = false;
const [AccountForm, accountApi] = useVbenForm(
  reactive({
    commonConfig: { hideLabel: true },
    schema: computed((): VbenFormSchema[] => [
      {
        component: 'VbenInput',
        fieldName: 'username',
        label: $t('authentication.username'),
        componentProps: {
          autocomplete: 'username',
          maxlength: 64,
          placeholder: $t('authentication.usernameTip'),
        },
        rules: z
          .string({ required_error: $t('authentication.usernameTip') })
          .min(1, $t('authentication.usernameTip'))
          .max(64),
      },
    ]),
    showDefaultActions: false,
  }),
);
const [ResetForm, resetApi] = useVbenForm(
  reactive({
    commonConfig: { hideLabel: true },
    schema: computed((): VbenFormSchema[] => [
      {
        component: 'VbenInput',
        fieldName: 'code',
        label: $t('account.recovery.code'),
        componentProps: {
          autocomplete: 'one-time-code',
          inputmode: 'numeric',
          maxlength: 6,
          placeholder: $t('account.recovery.codePlaceholder'),
        },
        rules: z
          .string({ required_error: $t('account.recovery.codeRule') })
          .regex(/^\d{6}$/, $t('account.recovery.codeRule')),
      },
      {
        component: 'VbenInputPassword',
        fieldName: 'newPassword',
        label: $t('profile.password.newPassword'),
        componentProps: {
          autocomplete: 'new-password',
          maxlength: 64,
          passwordStrength: true,
          placeholder: $t('profile.password.newPasswordPlaceholder'),
        },
        rules: z
          .string({ required_error: $t('account.recovery.passwordRule') })
          .regex(
            /^(?=.*[a-z])(?=.*\d)[\x21-\x7E]{8,64}$/i,
            $t('account.recovery.passwordRule'),
          ),
      },
      {
        component: 'VbenInputPassword',
        fieldName: 'confirmPassword',
        label: $t('profile.password.confirmPassword'),
        componentProps: {
          autocomplete: 'new-password',
          maxlength: 64,
          placeholder: $t('profile.password.confirmAgain'),
        },
        dependencies: {
          triggerFields: ['newPassword'],
          rules: (values) =>
            z
              .string({ required_error: $t('profile.password.confirmAgain') })
              .min(1, $t('profile.password.confirmAgain'))
              .refine(
                (value) => value === values.newPassword,
                $t('profile.password.passwordMismatch'),
              ),
        },
      },
    ]),
    showDefaultActions: false,
  }),
);
async function loadOptions() {
  loadFailed.value = false;
  try {
    const result = await getRecoveryOptionsApi();
    if (!disposed) {
      enabled.value = result.enabled;
      loaded.value = true;
    }
  } catch {
    if (!disposed) loadFailed.value = true;
  }
}
async function beginSend() {
  if (busy.value || remaining.value || !enabled.value) return;
  const { valid } = await accountApi.validate();
  if (!valid || disposed) return;
  username = (await accountApi.getValues()).username;
  captchaDialog.value?.open();
}
async function send(captcha: CaptchaVerification) {
  busy.value = true;
  try {
    const result = await sendRecoveryCodeApi({ username, ...captcha });
    if (disposed) return;
    challengeId = result.challengeId;
    start();
    step.value = 'reset';
    await nextTick();
    await resetApi.resetForm();
  } finally {
    busy.value = false;
  }
}
async function resetPassword() {
  if (busy.value || !challengeId) return;
  busy.value = true;
  try {
    const { valid } = await resetApi.validate();
    if (!valid || disposed) return;
    const values = await resetApi.getValues();
    await resetForgottenPasswordApi({
      challengeId,
      code: values.code,
      newPassword: values.newPassword,
      confirmPassword: values.confirmPassword,
    });
    if (!disposed) {
      await resetApi.resetForm();
      challengeId = '';
      step.value = 'done';
    }
  } catch {
    /* 请求客户端显示错误，保留输入以便修正。 */
  } finally {
    busy.value = false;
  }
}
async function restart() {
  challengeId = '';
  await resetApi.resetForm();
  step.value = 'account';
}
onMounted(loadOptions);
onBeforeUnmount(() => {
  disposed = true;
  username = '';
  challengeId = '';
});
</script>
<template>
  <div v-bind="$attrs" class="space-y-5">
    <div>
      <h1 class="mb-3 text-3xl font-semibold">
        {{ $t('account.recovery.title') }}
      </h1>
      <p class="text-sm text-muted-foreground">
        {{ $t('account.recovery.description') }}
      </p>
    </div>
    <p v-if="!loaded && !loadFailed" role="status">
      {{ $t('account.recovery.loading') }}
    </p>
    <VbenButton v-if="loadFailed" variant="outline" @click="loadOptions">{{
      $t('account.recovery.retry')
    }}</VbenButton>
    <p
      v-if="loaded && !enabled"
      role="alert"
      class="rounded-md bg-muted p-4 text-sm"
    >
      {{ $t('account.recovery.unavailable') }}
    </p>
    <template v-if="loaded && enabled">
      <div v-show="step === 'account'" @keydown.enter.prevent="beginSend">
        <AccountForm />
        <VbenButton
          class="w-full"
          :loading="busy"
          :disabled="busy || remaining > 0"
          @click="beginSend"
          >{{
            remaining
              ? $t('account.recovery.countdown', { seconds: remaining })
              : $t('account.recovery.send')
          }}</VbenButton
        >
      </div>
      <div
        v-show="step === 'reset'"
        class="space-y-4"
        @keydown.enter.prevent="resetPassword"
      >
        <p role="status" class="text-sm text-muted-foreground">
          {{ $t('account.recovery.sent') }}
        </p>
        <ResetForm />
        <VbenButton
          class="w-full"
          :loading="busy"
          :disabled="busy"
          @click="resetPassword"
          >{{ $t('account.recovery.reset') }}</VbenButton
        >
        <VbenButton
          variant="outline"
          class="w-full"
          :disabled="busy || remaining > 0"
          @click="restart"
          >{{
            remaining
              ? $t('account.recovery.countdown', { seconds: remaining })
              : $t('account.recovery.restart')
          }}</VbenButton
        >
      </div>
      <p
        v-if="step === 'done'"
        role="status"
        class="rounded-md bg-primary/10 p-4 text-sm text-primary"
      >
        {{ $t('account.recovery.success') }}
      </p>
      <p v-if="step !== 'done'" class="text-xs text-muted-foreground">
        {{ $t('account.recovery.unbound') }}
      </p>
    </template>
    <VbenButton
      variant="outline"
      class="w-full"
      :disabled="busy"
      @click="router.replace(LOGIN_PATH)"
      >{{ $t('account.recovery.back') }}</VbenButton
    >
    <CaptchaDialog ref="captchaDialog" :verify="send" />
  </div>
</template>
