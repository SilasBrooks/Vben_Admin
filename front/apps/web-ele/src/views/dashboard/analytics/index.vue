<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { DashboardSummary } from '#/api/dashboard';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { ElCard, ElEmpty, ElTable, ElTableColumn, ElTag } from 'element-plus';

import { getDashboardSummaryApi } from '#/api/dashboard';
import { $t } from '#/locales';

const summary = ref<DashboardSummary>();

const tokenTrendRef = ref<EchartsUIType>();
const { renderEcharts: renderTokenTrend } = useEcharts(tokenTrendRef);

const deptRef = ref<EchartsUIType>();
const { renderEcharts: renderDept } = useEcharts(deptRef);

const modelRef = ref<EchartsUIType>();
const { renderEcharts: renderModel } = useEcharts(modelRef);

const moduleRef = ref<EchartsUIType>();
const { renderEcharts: renderModule } = useEcharts(moduleRef);

/** 是否有模型用量数据（无数据展示空态） */
const hasModelData = ref(false);

/** 6 张概览卡（累计登录/累计操作受监控权限控制，无权限为 null 时对应卡不渲染） */
const overviewCards = ref<{ color: string; label: string; value: number | null }[]>([
  { color: '#409eff', label: $t('dashboard.analytics.card.users'), value: 0 },
  { color: '#67c23a', label: $t('dashboard.analytics.card.roles'), value: 0 },
  { color: '#e6a23c', label: $t('dashboard.analytics.card.logins'), value: 0 },
  { color: '#f56c6c', label: $t('dashboard.analytics.card.opers'), value: 0 },
  { color: '#8b5cf6', label: $t('dashboard.analytics.card.aiCalls'), value: 0 },
  { color: '#fa8c16', label: $t('dashboard.analytics.card.aiTokens'), value: 0 },
]);

function formatTime(value?: string) {
  return value ? (value.split('.')[0] ?? '').replace('T', ' ') : '';
}

onMounted(async () => {
  summary.value = await getDashboardSummaryApi();
  const { aiUsage, moduleDistribution, totals } = summary.value;

  overviewCards.value = [
    { color: '#409eff', label: $t('dashboard.analytics.card.users'), value: totals.userCount },
    { color: '#67c23a', label: $t('dashboard.analytics.card.roles'), value: totals.roleCount },
    { color: '#e6a23c', label: $t('dashboard.analytics.card.logins'), value: totals.loginCount },
    { color: '#f56c6c', label: $t('dashboard.analytics.card.opers'), value: totals.operCount },
    { color: '#8b5cf6', label: $t('dashboard.analytics.card.aiCalls'), value: aiUsage.totalCalls },
    { color: '#fa8c16', label: $t('dashboard.analytics.card.aiTokens'), value: aiUsage.totalTokens },
  ].filter((card) => card.value != null);

  // AI Token 趋势：柱=每日 token，线=调用次数（右轴）
  renderTokenTrend({
    grid: { bottom: 0, containLabel: true, left: '1%', right: '2%', top: '36px' },
    legend: { data: [$t('dashboard.common.tokenUsage'), $t('dashboard.common.callCount')], top: 0 },
    series: [
      {
        barMaxWidth: 24,
        data: aiUsage.tokenTrend.map((item) => item.tokens),
        itemStyle: { color: '#fa8c16' },
        name: $t('dashboard.common.tokenUsage'),
        type: 'bar',
      },
      {
        data: aiUsage.tokenTrend.map((item) => item.calls),
        itemStyle: { color: '#8b5cf6' },
        name: $t('dashboard.common.callCount'),
        smooth: true,
        yAxisIndex: 1,
        type: 'line',
      },
    ],
    tooltip: { trigger: 'axis' },
    xAxis: {
      axisTick: { show: false },
      data: aiUsage.tokenTrend.map((item) => item.date.slice(5)),
      type: 'category',
    },
    yAxis: [
      { minInterval: 1, type: 'value' },
      { minInterval: 1, type: 'value' },
    ],
  });

  // 部门人数分布饼图
  renderDept({
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        data: summary.value.deptDistribution,
        name: $t('dashboard.analytics.deptCount'),
        radius: '62%',
        type: 'pie',
      },
    ],
    tooltip: { trigger: 'item', formatter: `{b}: {c} ${$t('dashboard.analytics.person')} ({d}%)` },
  });

  // 模块操作分布环形图（近 14 天 Top5）；无操作日志权限时不下发数据，图不渲染
  if (moduleDistribution) {
    renderModule({
      legend: { bottom: 0, type: 'scroll' },
      series: [
        {
          data: moduleDistribution,
          name: $t('dashboard.analytics.operCount'),
          radius: ['38%', '62%'],
          type: 'pie',
        },
      ],
      tooltip: { trigger: 'item', formatter: `{b}: {c} ${$t('dashboard.analytics.times')} ({d}%)` },
    });
  }

  // 模型 Token 分布环形图（近 30 天 Top5，无数据展示空态）
  hasModelData.value = aiUsage.modelDistribution.length > 0;
  if (hasModelData.value) {
    renderModel({
      legend: { bottom: 0, type: 'scroll' },
      series: [
        {
          data: aiUsage.modelDistribution.map((item) => ({ name: item.name, value: item.tokens })),
          name: $t('dashboard.common.tokenUsage'),
          radius: ['38%', '62%'],
          type: 'pie',
        },
      ],
      tooltip: { trigger: 'item', formatter: `{b}: {c} ${$t('dashboard.common.tokenUsage')} ({d}%)` },
    });
  }
});
</script>

<template>
  <Page>
    <!-- 概览卡 -->
    <div class="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
      <ElCard v-for="card in overviewCards" :key="card.label" shadow="hover">
        <div class="text-sm text-gray-500">{{ card.label }}</div>
        <div class="mt-2 text-2xl font-bold" :style="{ color: card.color }">
          {{ card.value }}
        </div>
      </ElCard>
    </div>

    <!-- AI Token 用量趋势柱线图 -->
    <ElCard class="mt-5" shadow="never">
      <template #header>{{ $t('dashboard.analytics.tokenTrend') }}</template>
      <EchartsUI ref="tokenTrendRef" />
    </ElCard>

    <!-- 部门饼图 + 模型环形图 + 模块环形图 -->
    <div class="mt-5 grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
      <ElCard shadow="never">
        <template #header>{{ $t('dashboard.analytics.deptDistribution') }}</template>
        <EchartsUI ref="deptRef" />
      </ElCard>
      <ElCard shadow="never">
        <template #header>{{ $t('dashboard.analytics.modelDistribution') }}</template>
        <EchartsUI v-if="hasModelData" ref="modelRef" />
        <ElEmpty v-else :description="$t('dashboard.analytics.noModelData')" />
      </ElCard>
      <ElCard v-if="summary?.moduleDistribution" shadow="never">
        <template #header>{{ $t('dashboard.analytics.moduleDistribution') }}</template>
        <EchartsUI ref="moduleRef" />
      </ElCard>
    </div>

    <!-- 最近操作（无操作日志权限时整块隐藏） -->
    <ElCard v-if="summary?.recentOpers" class="mt-5" shadow="never">
      <template #header>{{ $t('dashboard.analytics.recentOpers') }}</template>
      <ElTable :data="summary?.recentOpers ?? []">
        <ElTableColumn prop="operName" :label="$t('dashboard.analytics.operUser')" width="120" />
        <ElTableColumn prop="module" :label="$t('dashboard.analytics.module')" width="140" />
        <ElTableColumn prop="description" :label="$t('dashboard.analytics.action')" min-width="160" />
        <ElTableColumn :label="$t('dashboard.analytics.cost')" width="100">
          <template #default="{ row }">{{ row.costMs }} ms</template>
        </ElTableColumn>
        <ElTableColumn :label="$t('dashboard.common.time')" min-width="160">
          <template #default="{ row }">{{ formatTime(row.operTime) }}</template>
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
