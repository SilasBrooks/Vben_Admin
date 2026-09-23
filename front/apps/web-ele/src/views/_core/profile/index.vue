<script setup lang="ts">
import { computed, ref } from 'vue';

import { Profile } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { $t } from '#/locales';

import ProfileBase from './base-setting.vue';
import ProfilePasswordSetting from './password-setting.vue';
import ProfileEmailSetting from './email-setting.vue';

const userStore = useUserStore();

const tabsValue = ref<string>('basic');

const tabs = computed(() => [
  {
    label: $t('profile.index.basic'),
    value: 'basic',
  },
  {
    label: $t('profile.index.passwordTab'),
    value: 'password',
  },
  { label: $t('account.binding.title'), value: 'email' },
]);
</script>
<template>
  <Profile
    v-model:model-value="tabsValue"
    :title="$t('profile.index.title')"
    :user-info="userStore.userInfo"
    :tabs="tabs"
  >
    <template #content>
      <ProfileBase v-if="tabsValue === 'basic'" />
      <ProfilePasswordSetting v-if="tabsValue === 'password'" />
      <ProfileEmailSetting v-if="tabsValue === 'email'" />
    </template>
  </Profile>
</template>
