<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { KbEvalCaseItem, KbEvalRunItem, KbEvalSetItem } from '#/api/system/kbEval';

import { nextTick, ref } from 'vue';

import { Page, VbenButton, useVbenModal } from '@vben/common-ui';

import { ElAlert, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteKbEvalCaseApi,
  deleteKbEvalSetApi,
  getKbEvalCasesApi,
  getKbEvalRunsApi,
  getKbEvalSetsApi,
  runKbEvalApi,
} from '#/api/system/kbEval';
import { $t } from '#/locales';

import CaseForm from './case-form.vue';
import RunReport from './run-report.vue';
import SetForm from './set-form.vue';

// ------------------------------------------------------------------
// 弹窗：评测集表单 / 用例表单 / 运行报告
// ------------------------------------------------------------------

const [SetFormModal, setFormApi] = useVbenModal({
  connectedComponent: SetForm,
});
const [CaseFormModal, caseFormApi] = useVbenModal({
  connectedComponent: CaseForm,
});
const [RunReportModal, runReportApi] = useVbenModal({
  connectedComponent: RunReport,
});

/** 时间列展示：去掉 LocalDateTime 微秒尾巴与 T 分隔符 */
function fmtTime(value?: null | string) {
  return typeof value === 'string'
    ? (value.split('.')[0]?.replace('T', ' ') ?? value)
    : '';
}

// ------------------------------------------------------------------
// 区一：评测集列表（行点击选中，驱动下方用例与运行历史）
// ------------------------------------------------------------------

const selectedSet = ref<KbEvalSetItem | null>(null);

const setGridOptions: VxeTableGridOptions<KbEvalSetItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'name', title: $t('kbEval.setName'), minWidth: 160, showOverflow: true },
    { field: 'caseCount', title: $t('kbEval.caseCount'), width: 80 },
    {
      field: 'lastMrr',
      title: $t('kbEval.lastMrr'),
      width: 100,
      formatter: ({ cellValue }) =>
        cellValue === null || cellValue === undefined ? '-' : cellValue.toFixed(4),
    },
    { field: 'description', title: $t('kbEval.setDescription'), minWidth: 180, showOverflow: true },
    {
      field: 'createTime',
      title: $t('kbEval.createTime'),
      width: 165,
      formatter: ({ cellValue }) => fmtTime(cellValue as null | string),
    },
    {
      title: $t('kbEval.action'),
      width: 140,
      fixed: 'right',
      slots: { default: 'setAction' },
    },
  ],
  height: 280,
  pagerConfig: { enabled: true },
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        return await getKbEvalSetsApi({
          name: formValues?.name,
          pageNo: page.currentPage,
          pageSize: page.pageSize,
        });
      },
    },
  },
  rowConfig: { isCurrent: true, isHover: true },
  toolbarConfig: {
    custom: true,
    refresh: true,
    refreshOptions: { code: 'query' },
    search: true,
  },
};

const [SetGrid, setGridApi] = useVbenVxeGrid({
  gridOptions: setGridOptions,
  gridEvents: { cellClick: selectSet },
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { placeholder: $t('kbEval.setNameSearchPlaceholder') },
        fieldName: 'name',
        label: $t('kbEval.setNameSearch'),
      },
    ],
  },
});

function openSetAdd() {
  setFormApi.setData({}).open();
}

/** 评测集保存后：刷新列表；若编辑的是选中集合，同步区二标签名称 */
function onSetSaved() {
  const data = setFormApi.getData<{ id?: number; name?: string }>();
  if (selectedSet.value && data?.id && data.id === selectedSet.value.id && data.name) {
    selectedSet.value = { ...selectedSet.value, name: data.name };
  }
  nextTick(() => setGridApi.query());
}

function openSetEdit(row: KbEvalSetItem) {
  setFormApi.setData({ ...row }).open();
}

async function removeSet(row: KbEvalSetItem) {
  await ElMessageBox.confirm(
    $t('kbEval.deleteSetConfirm', { name: row.name, n: row.caseCount }),
    $t('kbEval.deleteTitle'),
    { type: 'warning' },
  );
  await deleteKbEvalSetApi(row.id);
  ElMessage.success($t('kbEval.deleteSuccess'));
  // 删除的是当前选中集合时清空下两区，避免残留数据误导
  if (selectedSet.value?.id === row.id) {
    selectedSet.value = null;
  }
  setGridApi.query();
}

/** 行点击选中集合：高亮当前行并刷新用例/运行历史两区 */
function selectSet({ row }: { row: KbEvalSetItem }) {
  selectedSet.value = row;
  setGridApi.grid.setCurrentRow(row);
  nextTick(() => {
    caseGridApi.query();
    runGridApi.query();
  });
}

// ------------------------------------------------------------------
// 区二：评测用例（跟随选中集合，无分页）
// ------------------------------------------------------------------

const caseGridOptions: VxeTableGridOptions<KbEvalCaseItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'query', title: $t('kbEval.queryLabel'), minWidth: 220, showOverflow: true },
    {
      field: 'expectDocName',
      title: $t('kbEval.expectDoc'),
      minWidth: 150,
      showOverflow: true,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      field: 'expectSeq',
      title: $t('kbEval.expectSeqCol'),
      width: 90,
      formatter: ({ cellValue }) => (cellValue === null || cellValue === undefined ? '-' : String(cellValue)),
    },
    {
      field: 'note',
      title: $t('kbEval.note'),
      minWidth: 140,
      showOverflow: true,
      formatter: ({ cellValue }) => cellValue || '-',
    },
    {
      title: $t('kbEval.action'),
      width: 140,
      fixed: 'right',
      slots: { default: 'caseAction' },
    },
  ],
  height: 220,
  pagerConfig: { enabled: false },
  proxyConfig: {
    ajax: {
      query: async () => {
        if (!selectedSet.value) {
          return { items: [], total: 0 };
        }
        const list = await getKbEvalCasesApi(selectedSet.value.id);
        return { items: list, total: list.length };
      },
    },
  },
};

const [CaseGrid, caseGridApi] = useVbenVxeGrid({
  gridOptions: caseGridOptions,
});

function openCaseAdd() {
  if (!selectedSet.value) {
    return;
  }
  caseFormApi.setData({ setId: selectedSet.value.id }).open();
}

function openCaseEdit(row: KbEvalCaseItem) {
  caseFormApi.setData({ ...row }).open();
}

async function removeCase(row: KbEvalCaseItem) {
  await ElMessageBox.confirm($t('kbEval.deleteCaseConfirm'), $t('kbEval.deleteTitle'), {
    type: 'warning',
  });
  await deleteKbEvalCaseApi(row.id);
  ElMessage.success($t('kbEval.deleteSuccess'));
  refreshAfterMutation();
}

/** 用例增删后同步两处：用例列表刷新 + 评测集用例数概览刷新 */
function refreshAfterMutation() {
  nextTick(() => {
    caseGridApi.query();
    setGridApi.query();
  });
}

// ------------------------------------------------------------------
// 区三：运行历史（快照记录）+ 执行评测
// ------------------------------------------------------------------

const running = ref(false);

const runGridOptions: VxeTableGridOptions<KbEvalRunItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    {
      field: 'createTime',
      title: $t('kbEval.runTime'),
      width: 165,
      formatter: ({ cellValue }) => fmtTime(cellValue as null | string),
    },
    {
      field: 'rerankActive',
      title: $t('kbEval.rerankCol'),
      width: 90,
      slots: { default: 'rerank' },
    },
    {
      field: 'embeddingModel',
      title: $t('kbEval.model'),
      minWidth: 130,
      showOverflow: true,
    },
    { field: 'totalCases', title: $t('kbEval.totalCases'), width: 75 },
    { field: 'hitAt1', title: 'Hit@1', width: 80 },
    { field: 'hitAt3', title: 'Hit@3', width: 80 },
    { field: 'hitAt5', title: 'Hit@5', width: 80 },
    { field: 'mrr', title: 'MRR', width: 80 },
    { field: 'elapsedMs', title: $t('kbEval.elapsed'), width: 90 },
    {
      title: $t('kbEval.action'),
      width: 110,
      fixed: 'right',
      slots: { default: 'runAction' },
    },
  ],
  height: 240,
  pagerConfig: { enabled: false },
  proxyConfig: {
    ajax: {
      query: async () => {
        if (!selectedSet.value) {
          return { items: [], total: 0 };
        }
        const list = await getKbEvalRunsApi(selectedSet.value.id);
        return { items: list, total: list.length };
      },
    },
  },
};

const [RunGrid, runGridApi] = useVbenVxeGrid({
  gridOptions: runGridOptions,
});

/** 执行评测：同步调用（后端逐用例检索并计算指标），完成后弹出报告 */
async function runEval() {
  if (!selectedSet.value || running.value) {
    return;
  }
  running.value = true;
  try {
    const run = await runKbEvalApi(selectedSet.value.id);
    ElMessage.success($t('kbEval.runSuccess'));
    runReportApi.setData({ runId: run.id }).open();
    nextTick(() => {
      runGridApi.query();
      setGridApi.query();
    });
  } finally {
    running.value = false;
  }
}

function openReport(row: KbEvalRunItem) {
  runReportApi.setData({ runId: row.id }).open();
}
</script>

<template>
  <Page>
    <RunReportModal />
    <SetFormModal @saved="onSetSaved" />
    <CaseFormModal @saved="refreshAfterMutation" />

    <!-- 区一：评测集列表 -->
    <div class="mb-2">
      <SetGrid>
        <template #toolbar-actions>
          <VbenButton v-access:code="'Kb:Eval:Manage'" variant="default" @click="openSetAdd">
            {{ $t('kbEval.add') }}
          </VbenButton>
        </template>
        <template #setAction="{ row }">
          <VbenButton
            v-access:code="'Kb:Eval:Manage'"
            variant="link"
            size="sm"
            @click="openSetEdit(row as KbEvalSetItem)"
          >
            {{ $t('kbEval.edit') }}
          </VbenButton>
          <VbenButton
            v-access:code="'Kb:Eval:Manage'"
            variant="link"
            size="sm"
            class="text-destructive"
            @click="removeSet(row as KbEvalSetItem)"
          >
            {{ $t('kbEval.delete') }}
          </VbenButton>
        </template>
      </SetGrid>
    </div>

    <template v-if="selectedSet">
      <!-- 区二：选中集合的用例管理 + 执行评测 -->
      <div class="mb-2">
        <CaseGrid>
          <template #toolbar-actions>
            <div class="flex items-center gap-2">
              <ElTag type="primary" size="small" effect="dark">
                {{ $t('kbEval.selectedSet') }}: {{ selectedSet.name }}
              </ElTag>
              <VbenButton
                v-access:code="'Kb:Eval:Manage'"
                variant="default"
                @click="openCaseAdd"
              >
                {{ $t('kbEval.addCase') }}
              </VbenButton>
              <VbenButton
                v-access:code="'Kb:Eval:Run'"
                variant="default"
                :loading="running"
                @click="runEval"
              >
                {{ running ? $t('kbEval.running') : $t('kbEval.run') }}
              </VbenButton>
            </div>
          </template>
          <template #caseAction="{ row }">
            <VbenButton
              v-access:code="'Kb:Eval:Manage'"
              variant="link"
              size="sm"
              @click="openCaseEdit(row as KbEvalCaseItem)"
            >
              {{ $t('kbEval.edit') }}
            </VbenButton>
            <VbenButton
              v-access:code="'Kb:Eval:Manage'"
              variant="link"
              size="sm"
              class="text-destructive"
              @click="removeCase(row as KbEvalCaseItem)"
            >
              {{ $t('kbEval.delete') }}
            </VbenButton>
          </template>
        </CaseGrid>
      </div>

      <!-- 区三：运行历史（快照） -->
      <div>
        <RunGrid>
          <template #rerank="{ row }">
            <ElTag
              size="small"
              :type="(row as KbEvalRunItem).rerankActive ? 'warning' : 'info'"
              :effect="(row as KbEvalRunItem).rerankActive ? 'dark' : 'plain'"
            >
              {{
                (row as KbEvalRunItem).rerankActive
                  ? $t('kbEval.rerankOn')
                  : $t('kbEval.rerankOff')
              }}
            </ElTag>
          </template>
          <template #runAction="{ row }">
            <VbenButton variant="link" size="sm" @click="openReport(row as KbEvalRunItem)">
              {{ $t('kbEval.viewReport') }}
            </VbenButton>
          </template>
        </RunGrid>
      </div>
    </template>
    <ElAlert
      v-else
      :title="$t('kbEval.selectHint')"
      type="info"
      :closable="false"
      show-icon
    />
  </Page>
</template>
