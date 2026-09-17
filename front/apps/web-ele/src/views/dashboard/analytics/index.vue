<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { DashboardSummary } from '#/api/dashboard';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { ElCard, ElTable, ElTableColumn, ElTag } from 'element-plus';

import { getDashboardSummaryApi } from '#/api/dashboard';

const summary = ref<DashboardSummary>();

const trendRef = ref<EchartsUIType>();
const { renderEcharts: renderTrend } = useEcharts(trendRef);

const deptRef = ref<EchartsUIType>();
const { renderEcharts: renderDept } = useEcharts(deptRef);

const moduleRef = ref<EchartsUIType>();
const { renderEcharts: renderModule } = useEcharts(moduleRef);

/** 4 张概览卡 */
const overviewCards = ref([
  { color: '#409eff', label: '总用户', value: 0 },
  { color: '#67c23a', label: '总角色', value: 0 },
  { color: '#e6a23c', label: '累计登录', value: 0 },
  { color: '#f56c6c', label: '累计操作', value: 0 },
]);

function formatTime(value?: string) {
  return value ? value.split('.')[0].replace('T', ' ') : '';
}

onMounted(async () => {
  summary.value = await getDashboardSummaryApi();
  const { loginTrend, totals } = summary.value;

  overviewCards.value = [
    { color: '#409eff', label: '总用户', value: totals.userCount },
    { color: '#67c23a', label: '总角色', value: totals.roleCount },
    { color: '#e6a23c', label: '累计登录', value: totals.loginCount },
    { color: '#f56c6c', label: '累计操作', value: totals.operCount },
  ];

  // 柱线组合：柱=登录成功，线=登录失败
  renderTrend({
    grid: { bottom: 0, containLabel: true, left: '1%', right: '2%', top: '36px' },
    legend: { data: ['登录成功', '登录失败'], top: 0 },
    series: [
      {
        barMaxWidth: 24,
        data: loginTrend.map((item) => item.success),
        itemStyle: { color: '#409eff' },
        name: '登录成功',
        type: 'bar',
      },
      {
        data: loginTrend.map((item) => item.fail),
        itemStyle: { color: '#f56c6c' },
        name: '登录失败',
        smooth: true,
        type: 'line',
      },
    ],
    tooltip: { trigger: 'axis' },
    xAxis: {
      axisTick: { show: false },
      data: loginTrend.map((item) => item.date.slice(5)),
      type: 'category',
    },
    yAxis: { minInterval: 1, type: 'value' },
  });

  // 部门人数分布饼图
  renderDept({
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        data: summary.value.deptDistribution,
        name: '部门人数',
        radius: '62%',
        type: 'pie',
      },
    ],
    tooltip: { trigger: 'item', formatter: '{b}: {c} 人 ({d}%)' },
  });

  // 操作模块分布环形图
  renderModule({
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        data: summary.value.moduleDistribution,
        name: '操作次数',
        radius: ['38%', '62%'],
        type: 'pie',
      },
    ],
    tooltip: { trigger: 'item', formatter: '{b}: {c} 次 ({d}%)' },
  });
});
</script>

<template>
  <Page>
    <!-- 概览卡 -->
    <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <ElCard v-for="card in overviewCards" :key="card.label" shadow="hover">
        <div class="text-sm text-gray-500">{{ card.label }}</div>
        <div class="mt-2 text-2xl font-bold" :style="{ color: card.color }">
          {{ card.value }}
        </div>
      </ElCard>
    </div>

    <!-- 登录趋势柱线图 -->
    <ElCard class="mt-5" shadow="never">
      <template #header>近 14 天登录趋势</template>
      <EchartsUI ref="trendRef" />
    </ElCard>

    <!-- 部门饼图 + 模块环形图 -->
    <div class="mt-5 grid grid-cols-1 gap-4 lg:grid-cols-2">
      <ElCard shadow="never">
        <template #header>部门人数分布</template>
        <EchartsUI ref="deptRef" />
      </ElCard>
      <ElCard shadow="never">
        <template #header>操作模块分布（近 14 天 Top5）</template>
        <EchartsUI ref="moduleRef" />
      </ElCard>
    </div>

    <!-- 最近操作 -->
    <ElCard class="mt-5" shadow="never">
      <template #header>最近操作</template>
      <ElTable :data="summary?.recentOpers ?? []">
        <ElTableColumn prop="operName" label="操作人" width="120" />
        <ElTableColumn prop="module" label="模块" width="140" />
        <ElTableColumn prop="description" label="动作" min-width="160" />
        <ElTableColumn label="耗时" width="100">
          <template #default="{ row }">{{ row.costMs }} ms</template>
        </ElTableColumn>
        <ElTableColumn label="时间" min-width="160">
          <template #default="{ row }">{{ formatTime(row.operTime) }}</template>
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
  </Page>
</template>
