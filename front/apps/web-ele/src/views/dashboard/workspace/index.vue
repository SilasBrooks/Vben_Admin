<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { DashboardSummary } from '#/api/dashboard';

import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';
import { preferences } from '@vben/preferences';
import { useUserStore } from '@vben/stores';

import {
  ElAvatar,
  ElCard,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getDashboardSummaryApi } from '#/api/dashboard';
import { $t } from '#/locales';

const userStore = useUserStore();
const router = useRouter();

const summary = ref<DashboardSummary>();

const trendRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(trendRef);

/** 时段问候 */
const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return $t('dashboard.workspace.greeting.dawn');
  if (hour < 12) return $t('dashboard.workspace.greeting.morning');
  if (hour < 18) return $t('dashboard.workspace.greeting.afternoon');
  return $t('dashboard.workspace.greeting.evening');
});

/** 今日日期（xxxx年x月x日 星期x） */
const todayText = computed(() => {
  const now = new Date();
  const week = $t('dashboard.workspace.weekdays').split(',')?.[now.getDay()];
  return $t('dashboard.workspace.date', {
    d: now.getDate(),
    m: now.getMonth() + 1,
    w: week,
    y: now.getFullYear(),
  });
});

/** 6 张今日概况卡：今日发生了什么（累计规模指标由分析页承载），点击跳转对应管理页；受监控权限控制的计数无权限时为 null，对应卡片不渲染 */
const statCards = computed(() => {
  const today = summary.value?.today;
  return [
    { color: '#67c23a', label: $t('dashboard.workspace.stat.todayLoginSuccess'), path: '/monitor/login-log', value: today?.loginSuccess },
    { color: '#f56c6c', label: $t('dashboard.workspace.stat.todayLoginFail'), path: '/monitor/login-log', value: today?.loginFail },
    { color: '#e6a23c', label: $t('dashboard.workspace.stat.todayOper'), path: '/monitor/oper-log', value: today?.operCount },
    { color: '#36cfc9', label: $t('dashboard.workspace.stat.online'), path: '/monitor/online', value: summary.value?.totals.onlineCount ?? 0 },
    { color: '#8b5cf6', label: $t('dashboard.workspace.stat.todayAiCalls'), path: '/analytics', value: summary.value?.aiUsage.todayCalls ?? 0 },
    { color: '#fa8c16', label: $t('dashboard.workspace.stat.todayAiTokens'), path: '/analytics', value: summary.value?.aiUsage.todayTokens ?? 0 },
  ].filter((card) => card.value != null);
});

/** 快捷入口：按当前用户权限码显隐（v-access:code 控制模板渲染）；icon 为完整类名字面量，保证 Tailwind 扫描生成 */
const quickNavs = [
  { code: '', color: '#8b5cf6', icon: 'icon-[ant-design--message-outlined]', label: $t('dashboard.workspace.nav.imChat'), path: '/im' },
  { code: 'Kb:Doc:List', color: '#36cfc9', icon: 'icon-[ant-design--database-outlined]', label: $t('dashboard.workspace.nav.kb'), path: '/system/kb' },
  { code: 'Kb:Eval:List', color: '#fa541c', icon: 'icon-[ant-design--experiment-outlined]', label: $t('dashboard.workspace.nav.kbEval'), path: '/system/kb-eval' },
  { code: 'System:Llm:List', color: '#409eff', icon: 'icon-[ant-design--robot-outlined]', label: $t('dashboard.workspace.nav.llm'), path: '/system/llm' },
  { code: 'System:User:List', color: '#67c23a', icon: 'icon-[ant-design--team-outlined]', label: $t('dashboard.workspace.nav.user'), path: '/system/user' },
  { code: 'System:Dict:List', color: '#e6a23c', icon: 'icon-[ant-design--book-outlined]', label: $t('dashboard.workspace.nav.dict'), path: '/system/dict' },
  { code: 'Monitor:Online:List', color: '#f56c6c', icon: 'icon-[ant-design--eye-outlined]', label: $t('dashboard.workspace.nav.online'), path: '/monitor/online' },
];

function formatTime(value?: string) {
  return value ? (value.split('.')[0] ?? '').replace('T', ' ') : '';
}

onMounted(async () => {
  summary.value = await getDashboardSummaryApi();
  const { loginTrend } = summary.value;

  // 登录趋势：成功/失败双折线（工作台唯一趋势图，AI 趋势图由分析页承载）；无登录日志权限时不下发数据，图不渲染
  if (loginTrend) {
    renderEcharts({
      grid: { bottom: 0, containLabel: true, left: '1%', right: '2%', top: '36px' },
      legend: { data: [$t('dashboard.common.loginSuccess'), $t('dashboard.common.loginFail')], top: 0 },
      series: [
        {
          areaStyle: { opacity: 0.15 },
          data: loginTrend.map((item) => item.success),
          itemStyle: { color: '#67c23a' },
          name: $t('dashboard.common.loginSuccess'),
          smooth: true,
          type: 'line',
        },
        {
          areaStyle: { opacity: 0.15 },
          data: loginTrend.map((item) => item.fail),
          itemStyle: { color: '#f56c6c' },
          name: $t('dashboard.common.loginFail'),
          smooth: true,
          type: 'line',
        },
      ],
      tooltip: { trigger: 'axis' },
      xAxis: {
        axisTick: { show: false },
        boundaryGap: false,
        data: loginTrend.map((item) => item.date.slice(5)),
        type: 'category',
      },
      yAxis: { minInterval: 1, type: 'value' },
    });
  }
});
</script>

<template>
  <Page>
    <!-- 欢迎横幅 -->
    <ElCard shadow="never" :body-style="{ padding: 0 }">
      <div
        class="flex items-center gap-4 rounded-[inherit] p-6"
        style="background: linear-gradient(120deg, #1890ff 0%, #36cfc9 100%); color: #fff"
      >
        <ElAvatar
          :size="56"
          :src="userStore.userInfo?.avatar || preferences.app.defaultAvatar"
        />
        <div>
          <div class="text-lg font-semibold">
            {{ $t('dashboard.workspace.welcome', { greeting, name: userStore.userInfo?.realName }) }}
          </div>
          <div class="mt-1 text-sm opacity-80">{{ $t('dashboard.workspace.today', { today: todayText }) }}</div>
        </div>
      </div>
    </ElCard>

    <!-- 今日概况卡 -->
    <div class="mt-5 grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-6">
      <ElCard
        v-for="card in statCards"
        :key="card.label"
        shadow="hover"
        class="cursor-pointer"
        @click="router.push(card.path)"
      >
        <div class="text-sm text-gray-500">{{ card.label }}</div>
        <div class="mt-2 text-2xl font-bold" :style="{ color: card.color }">
          {{ card.value }}
        </div>
      </ElCard>
    </div>

    <!-- 快捷入口（仅展示当前用户有权限的页面） -->
    <ElCard class="mt-5" shadow="never">
      <template #header>{{ $t('dashboard.workspace.quickNav') }}</template>
      <div class="grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-7">
        <template v-for="nav in quickNavs" :key="nav.path">
          <div
            v-if="!nav.code"
            class="flex cursor-pointer flex-col items-center gap-2 rounded-md p-3 hover:bg-accent"
            @click="router.push(nav.path)"
          >
            <span class="text-2xl" :class="nav.icon" :style="{ color: nav.color }"></span>
            <span class="text-sm">{{ nav.label }}</span>
          </div>
          <div
            v-else
            v-access:code="nav.code"
            class="flex cursor-pointer flex-col items-center gap-2 rounded-md p-3 hover:bg-accent"
            @click="router.push(nav.path)"
          >
            <span class="text-2xl" :class="nav.icon" :style="{ color: nav.color }"></span>
            <span class="text-sm">{{ nav.label }}</span>
          </div>
        </template>
      </div>
    </ElCard>

    <!-- 登录趋势（工作台唯一趋势图，AI 用量趋势见分析页）；无登录日志权限时整块隐藏 -->
    <ElCard v-if="summary?.loginTrend" class="mt-5" shadow="never">
      <template #header>{{ $t('dashboard.common.loginTrend') }}</template>
      <EchartsUI ref="trendRef" />
    </ElCard>

    <!-- 最近登录（无登录日志权限时整块隐藏） -->
    <ElCard v-if="summary?.recentLogins" class="mt-5" shadow="never">
      <template #header>{{ $t('dashboard.workspace.recentLogins') }}</template>
      <ElTable :data="summary?.recentLogins ?? []">
        <ElTableColumn prop="username" :label="$t('dashboard.workspace.username')" width="120" />
        <ElTableColumn prop="ip" label="IP" min-width="130" />
        <ElTableColumn :label="$t('dashboard.common.time')" min-width="160">
          <template #default="{ row }">{{ formatTime(row.loginTime) }}</template>
        </ElTableColumn>
        <ElTableColumn :label="$t('dashboard.common.status')" width="90">
          <template #default="{ row }">
            <ElTag :type="row.status === 0 ? 'success' : 'danger'">
              {{ row.status === 0 ? $t('dashboard.common.success') : $t('dashboard.common.fail') }}
            </ElTag>
          </template>
        </ElTableColumn>
      </ElTable>
    </ElCard>
  </Page>
</template>
