<script setup lang="ts">
import type { VbenFormSchema } from '@vben/common-ui';
import { computed, h, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useVbenForm, VbenButton, z } from '@vben/common-ui';
import {
  bindRecoveryEmailApi,
  getRecoveryEmailApi,
  sendBindingCodeApi,
} from '#/api';
import { useEmailCodeCountdown } from '#/hooks/use-email-code-countdown';
import { $t } from '#/locales';
const loaded = ref(false);
const loadFailed = ref(false);
const enabled = ref(false);
const boundEmail = ref('');
const changingEmail = ref(false);
const targetEmail = ref('');
const challengeId = ref('');
const sending = ref(false);
const binding = ref(false);
const busy = computed(() => sending.value || binding.value);
const success = ref(false);
const { remaining, start } = useEmailCodeCountdown();
let password = '';
let disposed = false;
const [DetailsForm, detailsApi] = useVbenForm(
  reactive({
    layout: 'vertical',
    schema: computed((): VbenFormSchema[] => [
      {
        component: 'VbenInput',
        fieldName: 'email',
        label: $t('account.binding.email'),
        componentProps: {
          autocomplete: 'email',
          disabled: busy.value,
          maxlength: 255,
          placeholder: 'name@example.com',
        },
        rules: z
          .string({ required_error: $t('profile.base.emailInvalid') })
          .email($t('profile.base.emailInvalid'))
          .max(255),
      },
      {
        component: 'VbenInputPassword',
        fieldName: 'password',
        label: $t('account.binding.password'),
        componentProps: {
          autocomplete: 'current-password',
          disabled: busy.value,
          maxlength: 128,
          placeholder: $t('account.binding.passwordTip'),
        },
        rules: z
          .string({ required_error: $t('account.binding.passwordTip') })
          .min(1, $t('account.binding.passwordTip')),
      },
    ]),
    handleValuesChange(values) {
      success.value = false;
      if (
        challengeId.value &&
        (values.email !== targetEmail.value || values.password !== password)
      ) {
        void clearChallenge();
      }
    },
    showDefaultActions: false,
  }),
);
const [CodeForm, codeApi] = useVbenForm(
  reactive({
    layout: 'vertical',
    schema: computed((): VbenFormSchema[] => [
      {
        component: 'VbenInput',
        fieldName: 'code',
        label: $t('account.recovery.code'),
        controlClass: 'min-w-0',
        componentProps: {
          autocomplete: 'one-time-code',
          disabled: busy.value,
          inputmode: 'numeric',
          maxlength: 6,
          placeholder: $t('account.recovery.codePlaceholder'),
        },
        suffix: () =>
          h(
            VbenButton,
            {
              type: 'button',
              variant: 'outline',
              loading: sending.value,
              disabled: busy.value || remaining.value > 0,
              onClick: send,
            },
            () =>
              sending.value
                ? $t('account.binding.sending')
                : remaining.value
                  ? $t('account.binding.countdown', {
                      seconds: remaining.value,
                    })
                  : $t('account.recovery.send'),
          ),
        rules: z
          .string({ required_error: $t('account.recovery.codeRule') })
          .regex(/^\d{6}$/, $t('account.recovery.codeRule')),
      },
    ]),
    showDefaultActions: false,
  }),
);
async function load() {
  loadFailed.value = false;
  try {
    const result = await getRecoveryEmailApi();
    if (!disposed) {
      enabled.value = result.enabled;
      boundEmail.value = result.email;
      loaded.value = true;
    }
  } catch {
    if (!disposed) loadFailed.value = true;
  }
}
async function send() {
  if (busy.value || remaining.value || !enabled.value) return;
  sending.value = true;
  success.value = false;
  try {
    const { valid } = await detailsApi.validate();
    if (!valid || disposed) return;
    const values = await detailsApi.getValues();
    const result = await sendBindingCodeApi({
      email: values.email,
      password: values.password,
    });
    if (disposed) return;
    password = values.password;
    targetEmail.value = values.email;
    challengeId.value = result.challengeId;
    start();
    await codeApi.resetForm();
  } catch {
    /* 错误由请求客户端统一显示。 */
  } finally {
    sending.value = false;
  }
}
async function confirm() {
  if (busy.value || !enabled.value || !challengeId.value) return;
  binding.value = true;
  try {
    // 再检查一次当前输入，避免表单变更回调尚未执行时提交旧挑战。
    const details = await detailsApi.getValues();
    if (details.email !== targetEmail.value || details.password !== password) {
      await clearChallenge();
      return;
    }
    const { valid } = await codeApi.validate();
    if (!valid || disposed) return;
    const values = await codeApi.getValues();
    await bindRecoveryEmailApi({
      challengeId: challengeId.value,
      code: values.code,
      password,
    });
    if (disposed) return;
    password = '';
    challengeId.value = '';
    await detailsApi.resetForm();
    await codeApi.resetForm();
    await load();
    changingEmail.value = false;
    success.value = true;
  } catch {
    /* 保留验证码输入，允许在尝试次数内修正。 */
  } finally {
    binding.value = false;
  }
}
function changeEmail() {
  success.value = false;
  changingEmail.value = true;
}
async function clearChallenge() {
  challengeId.value = '';
  targetEmail.value = '';
  password = '';
  await codeApi.resetForm();
}
function handleEnter(event: KeyboardEvent) {
  if (event.target instanceof HTMLInputElement) {
    event.preventDefault();
    void confirm();
  }
}
onMounted(load);
onBeforeUnmount(() => {
  disposed = true;
  password = '';
});
</script>
<template>
  <div class="max-w-lg space-y-5">
    <p class="text-sm text-muted-foreground">
      {{ $t('account.binding.description') }}
    </p>
    <p v-if="!loaded && !loadFailed">{{ $t('account.recovery.loading') }}</p>
    <VbenButton v-if="loadFailed" variant="outline" @click="load">{{
      $t('account.recovery.retry')
    }}</VbenButton>
    <template v-if="loaded">
      <p>
        {{
          boundEmail
            ? $t('account.binding.bound', { email: boundEmail })
            : $t('account.binding.unbound')
        }}
      </p>
      <p v-if="!enabled" role="alert" class="rounded-md bg-muted p-4 text-sm">
        {{ $t('account.recovery.unavailable') }}
      </p>
      <p v-if="success" role="status" class="text-sm text-primary">
        {{ $t('account.binding.success') }}
      </p>
      <template v-if="enabled">
        <VbenButton
          v-if="boundEmail && !changingEmail"
          type="button"
          :disabled="busy"
          @click="changeEmail"
        >
          {{ $t('account.binding.change') }}
        </VbenButton>
        <div v-else class="space-y-2" @keydown.enter="handleEnter">
          <DetailsForm />
          <CodeForm />
          <p v-if="challengeId" role="status" class="pb-2 text-sm">
            {{ $t('account.binding.sent', { email: targetEmail }) }}
          </p>
          <VbenButton
            type="button"
            :loading="binding"
            :disabled="busy || !challengeId"
            @click="confirm"
          >
            {{ $t('account.binding.confirm') }}
          </VbenButton>
        </div>
      </template>
    </template>
  </div>
</template>
