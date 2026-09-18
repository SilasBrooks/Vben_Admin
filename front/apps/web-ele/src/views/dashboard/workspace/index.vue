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

const userStore = useUserStore();
const router = useRouter();

const summary = ref<DashboardSummary>();

const trendRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(trendRef);

/** 时段问候 */
const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return '凌晨好';
  if (hour < 12) return '上午好';
  if (hour < 18) return '下午好';
  return '晚上好';
});

/** 今日日期（xxxx年x月x日 星期x） */
const todayText = computed(() => {
  const now = new Date();
  const week = ['日', '一', '二', '三', '四', '五', '六']?.[now.getDay()];
  return `${now.getFullYear()} 年 ${now.getMonth() + 1} 月 ${now.getDate()} 日 星期${week}`;
});

/** 6 张统计卡：数值来自 summary，点击跳转对应管理页 */
const statCards = computed(() => {
  const totals = summary.value?.totals;
  return [
    { color: '#409eff', label: '用户总数', path: '/system/user', value: totals?.userCount ?? 0 },
    { color: '#67c23a', label: '角色总数', path: '/system/role', value: totals?.roleCount ?? 0 },
    { color: '#e6a23c', label: '部门总数', path: '/system/dept', value: totals?.deptCount ?? 0 },
    { color: '#f56c6c', label: '在线用户', path: '/monitor/online', value: totals?.onlineCount ?? 0 },
    { color: '#9a66e4', label: '文件总数', path: '/system/file', value: totals?.fileCount ?? 0 },
    { color: '#36cfc9', label: '今日登录', path: '/monitor/login-log', value: summary.value?.today.loginSuccess ?? 0 },
  ];
});

function formatTime(value?: string) {
  return value ? (value.split('.')[0] ?? '').replace('T', ' ') : '';
}

onMounted(async () => {
  summary.value = await getDashboardSummaryApi();
  const trend = summary.value.loginTrend;
  renderEcharts({
    grid: { bottom: 0, containLabel: true, left: '1%', right: '2%', top: '36px' },
    legend: { data: ['登录成功', '登录失败'], top: 0 },
    series: [
      {
        areaStyle: { opacity: 0.15 },
        data: trend.map((item) => item.success),
        itemStyle: { color: '#67c23a' },
        name: '登录成功',
        smooth: true,
        type: 'line',
      },
      {
        areaStyle: { opacity: 0.15 },
        data: trend.map((item) => item.fail),
        itemStyle: { color: '#f56c6c' },
        name: '登录失败',
        smooth: true,
        type: 'line',
      },
    ],
    tooltip: { trigger: 'axis' },
    xAxis: {
      axisTick: { show: false },
      boundaryGap: false,
      data: trend.map((item) => item.date.slice(5)),
      type: 'category',
    },
    yAxis: { minInterval: 1, type: 'value' },
  });
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
            {{ greeting }}，{{ userStore.userInfo?.realName }}，开始您一天的工作吧！
          </div>
          <div class="mt-1 text-sm opacity-80">今天是 {{ todayText }}</div>
        </div>
      </div>
    </ElCard>

    <!-- 统计卡 -->
    <div class="mt-5 grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
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

    <!-- 趋势图 + 最近登录 -->
    <div class="mt-5 grid grid-cols-1 gap-4 lg:grid-cols-2">
      <ElCard shadow="never">
        <template #header>近 14 天登录趋势</template>
        <EchartsUI ref="trendRef" />
      </ElCard>
      <ElCard shadow="never">
        <template #header>最近登录</template>
        <ElTable :data="summary?.recentLogins ?? []">
          <ElTableColumn prop="username" label="用户名" width="120" />
          <ElTableColumn prop="ip" label="IP" min-width="130" />
          <ElTableColumn label="时间" min-width="160">
            <template #default="{ row }">{{ formatTime(row.loginTime) }}</template>
          </ElTableColumn>
          <ElTableColumn label="状态" width="90">
            <template #default="{ row }">
              <ElTag :type="row.status === 0 ? 'success' : 'danger'">
                {{ row.status === 0 ? '成功' : '失败' }}
              </ElTag>
            </template>
          </ElTableColumn>
        </ElTable>
      </ElCard>
    </div>
  </Page>
</template>
