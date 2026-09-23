<script setup lang="ts">
import type { VbenFormSchema } from '@vben/common-ui';
import type { CaptchaVerification } from '#/api';

import { computed, onBeforeUnmount, reactive, ref } from 'vue';
import { useVbenForm, useVbenModal, VbenButton, z } from '@vben/common-ui';
import { getCaptchaApi } from '#/api';
import { $t } from '#/locales';

const props = defineProps<{
  closeOnVerifyError?: boolean;
  verify: (value: CaptchaVerification) => Promise<unknown>;
}>();
const image = ref('');
const captchaId = ref('');
const loading = ref(false);
const submitting = ref(false);
let generation = 0;
let disposed = false;
const [Form, formApi] = useVbenForm(
  reactive({
    commonConfig: { hideLabel: true },
    schema: computed((): VbenFormSchema[] => [
      {
        component: 'VbenInput',
        fieldName: 'captchaCode',
        label: $t('profile.login.captcha'),
        componentProps: {
          autocomplete: 'off',
          maxlength: 4,
          placeholder: $t('profile.login.captchaPlaceholder'),
        },
        rules: z
          .string({ required_error: $t('account.recovery.captchaRule') })
          .regex(/^[a-z\d]{4}$/i, $t('account.recovery.captchaRule')),
      },
    ]),
    showDefaultActions: false,
  }),
);
const [Modal, modalApi] = useVbenModal({
  onOpenChange(open) {
    if (open) void refresh();
    else {
      generation++;
      image.value = '';
      captchaId.value = '';
      loading.value = false;
    }
  },
  onConfirm: confirm,
});

async function refresh() {
  const request = ++generation;
  loading.value = true;
  captchaId.value = '';
  image.value = '';
  try {
    await formApi.resetForm({ values: { captchaCode: '' } });
    const result = await getCaptchaApi();
    if (!disposed && request === generation) {
      captchaId.value = result.captchaId;
      image.value = result.image;
    }
  } catch {
    // 请求客户端已显示错误；空图片时保留重新获取入口。
  } finally {
    if (!disposed && request === generation) loading.value = false;
  }
}

async function confirm() {
  if (submitting.value || loading.value || !captchaId.value) return;
  submitting.value = true;
  modalApi.lock();
  try {
    const { valid } = await formApi.validate();
    if (!valid || disposed) return;
    const values = await formApi.getValues();
    if (disposed) return;
    await props.verify({
      captchaId: captchaId.value,
      captchaCode: values.captchaCode,
    });
    if (!disposed) modalApi.close();
  } catch {
    if (!disposed) {
      if (props.closeOnVerifyError) await modalApi.close();
      else await refresh();
    }
  } finally {
    submitting.value = false;
    if (!disposed) modalApi.unlock();
  }
}

defineExpose({ open: () => modalApi.open() });
onBeforeUnmount(() => {
  disposed = true;
  generation++;
});
</script>

<template>
  <Modal
    :title="$t('profile.login.captchaTitle')"
    :confirm-disabled="loading || !captchaId"
    class="w-[400px]"
  >
    <div class="space-y-3 py-2" @keydown.enter.prevent="confirm">
      <VbenButton
        variant="outline"
        class="h-14 w-full"
        :aria-label="$t('profile.login.refreshCaptcha')"
        :disabled="loading || submitting"
        @click="refresh"
      >
        <img
          v-if="image"
          :src="image"
          :alt="$t('profile.login.captcha')"
          class="h-11 w-[132px]"
        />
        <span v-else>{{
          loading
            ? $t('profile.login.captchaLoading')
            : $t('account.recovery.captchaRetry')
        }}</span>
      </VbenButton>
      <p class="text-xs text-muted-foreground">
        {{ $t('account.recovery.captchaHint') }}
      </p>
      <Form />
    </div>
  </Modal>
</template>
