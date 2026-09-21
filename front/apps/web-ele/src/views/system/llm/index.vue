<script lang="ts" setup>
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { LlmItem } from '#/api/system/llm';

import { reactive, ref } from 'vue';

import { Page, VbenButton } from '@vben/common-ui';

import {
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTag,
} from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  activateLlmApi,
  createLlmApi,
  deleteLlmApi,
  getLlmListApi,
  testLlmApi,
  updateLlmApi,
} from '#/api/system/llm';

import { $t } from '#/locales';

// ------------------------------------------------------------------
// 列表
// ------------------------------------------------------------------

const gridOptions: VxeTableGridOptions<LlmItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    { field: 'name', title: $t('llm.name'), minWidth: 140, showOverflow: true },
    { field: 'model', title: $t('llm.model'), minWidth: 130, showOverflow: true },
    {
      field: 'baseUrl',
      title: $t('llm.baseUrl'),
      minWidth: 220,
      showOverflow: true,
    },
    { field: 'apiKey', title: $t('llm.apiKey'), minWidth: 140, showOverflow: true },
    {
      field: 'enabled',
      title: $t('llm.status'),
      width: 90,
      slots: { default: 'status' },
    },
    {
      field: 'isActive',
      title: $t('llm.active'),
      width: 90,
      slots: { default: 'active' },
    },
    {
      field: 'createTime',
      title: $t('llm.createTime'),
      width: 165,
      formatter: ({ cellValue }) =>
        typeof cellValue === 'string' ? cellValue.replace('T', ' ') : (cellValue ?? ''),
    },
    { title: $t('llm.action'), width: 220, fixed: 'right', slots: { default: 'action' } },
  ],
  pagerConfig: { enabled: true },
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        return await getLlmListApi({
          name: formValues?.name,
          pageNo: page.currentPage,
          pageSize: page.pageSize,
        });
      },
    },
  },
  toolbarConfig: {
    custom: true,
    refresh: true,
    refreshOptions: { code: 'query' },
    search: true,
  },
};

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions,
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { placeholder: $t('llm.nameSearchPlaceholder') },
        fieldName: 'name',
        label: $t('llm.nameSearch'),
      },
    ],
  },
});

// ------------------------------------------------------------------
// 新增/编辑表单
// ------------------------------------------------------------------

const dialogVisible = ref(false);
const editingId = ref<null | number>(null);
const saving = ref(false);

const form = reactive({
  apiKey: '',
  baseUrl: '',
  enabled: true,
  maxTokens: undefined as undefined | number,
  model: '',
  name: '',
  remark: '',
  temperature: undefined as undefined | number,
  timeoutSeconds: 60,
});

const formRef = ref<InstanceType<typeof ElForm>>();
const formRules = {
  baseUrl: [{ message: $t('llm.requiredHint'), required: true, trigger: 'blur' }],
  model: [{ message: $t('llm.requiredHint'), required: true, trigger: 'blur' }],
  name: [{ message: $t('llm.requiredHint'), required: true, trigger: 'blur' }],
};

function openAdd() {
  editingId.value = null;
  Object.assign(form, {
    apiKey: '',
    baseUrl: '',
    enabled: true,
    maxTokens: undefined,
    model: '',
    name: '',
    remark: '',
    temperature: undefined,
    timeoutSeconds: 60,
  });
  dialogVisible.value = true;
}

function openEdit(row: LlmItem) {
  editingId.value = row.id;
  Object.assign(form, {
    apiKey: '',
    baseUrl: row.baseUrl,
    enabled: row.enabled === 1,
    maxTokens: row.maxTokens ?? undefined,
    model: row.model,
    name: row.name,
    remark: row.remark ?? '',
    temperature: row.temperature ?? undefined,
    timeoutSeconds: row.timeoutSeconds || 60,
  });
  dialogVisible.value = true;
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    const payload = {
      apiKey: form.apiKey,
      baseUrl: form.baseUrl.trim(),
      enabled: (form.enabled ? 1 : 0) as 0 | 1,
      maxTokens: form.maxTokens ?? null,
      model: form.model.trim(),
      name: form.name.trim(),
      remark: form.remark || null,
      temperature: form.temperature ?? null,
      timeoutSeconds: form.timeoutSeconds || 60,
    };
    if (editingId.value == null) {
      await createLlmApi(payload);
    } else {
      await updateLlmApi(editingId.value, payload);
    }
    ElMessage.success($t('llm.saveSuccess'));
    dialogVisible.value = false;
    gridApi.reload();
  } finally {
    saving.value = false;
  }
}

// ------------------------------------------------------------------
// 行操作
// ------------------------------------------------------------------

async function testConnect(row: LlmItem) {
  const loading = ElMessage({
    message: $t('llm.testing'),
    type: 'info',
    duration: 0,
  });
  try {
    const result = await testLlmApi(row.id);
    loading.close();
    ElMessage.success(
      $t('llm.testSuccess', { model: result.model, ms: result.elapsedMs }),
    );
  } catch (error) {
    loading.close();
    const reason = error instanceof Error ? error.message : String(error);
    ElMessage.error($t('llm.testFailed', { reason }));
  }
}

async function activate(row: LlmItem) {
  await ElMessageBox.confirm(
    $t('llm.activateConfirm', { name: row.name }),
    $t('llm.activateTitle'),
    { type: 'warning' },
  );
  await activateLlmApi(row.id);
  ElMessage.success($t('llm.activateSuccess'));
  gridApi.reload();
}

async function remove(row: LlmItem) {
  if (row.isActive === 1) {
    ElMessage.warning($t('llm.deleteConfirm', { name: row.name }));
    return;
  }
  await ElMessageBox.confirm(
    $t('llm.deleteConfirm', { name: row.name }),
    $t('llm.deleteTitle'),
    { type: 'warning' },
  );
  await deleteLlmApi(row.id);
  ElMessage.success($t('llm.deleteSuccess'));
  gridApi.reload();
}
</script>

<template>
  <Page auto-content-height>
    <Grid>
      <template #toolbar-actions>
        <VbenButton
          v-access:code="'System:Llm:Add'"
          variant="default"
          @click="openAdd"
        >
          {{ $t('llm.add') }}
        </VbenButton>
      </template>

      <template #status="{ row }">
        <ElTag :type="(row as LlmItem).enabled === 1 ? 'success' : 'info'" size="small">
          {{
            (row as LlmItem).enabled === 1
              ? $t('llm.enabledOn')
              : $t('llm.enabledOff')
          }}
        </ElTag>
      </template>

      <template #active="{ row }">
        <ElTag
          :type="(row as LlmItem).isActive === 1 ? 'warning' : 'info'"
          size="small"
          :effect="(row as LlmItem).isActive === 1 ? 'dark' : 'plain'"
        >
          {{
            (row as LlmItem).isActive === 1 ? $t('llm.active') : $t('llm.inactive')
          }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <VbenButton
          v-access:code="'System:Llm:Edit'"
          variant="link"
          size="sm"
          @click="openEdit(row as LlmItem)"
        >
          {{ $t('llm.edit') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Llm:Edit'"
          variant="link"
          size="sm"
          @click="testConnect(row as LlmItem)"
        >
          {{ $t('llm.test') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Llm:Activate'"
          variant="link"
          size="sm"
          :disabled="(row as LlmItem).isActive === 1"
          @click="activate(row as LlmItem)"
        >
          {{ $t('llm.activate') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Llm:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          :disabled="(row as LlmItem).isActive === 1"
          @click="remove(row as LlmItem)"
        >
          {{ $t('llm.delete') }}
        </VbenButton>
      </template>
    </Grid>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId == null ? $t('llm.formTitleAdd') : $t('llm.formTitleEdit')"
      width="560px"
      destroy-on-close
    >
      <ElForm ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <ElFormItem :label="$t('llm.name')" prop="name">
          <ElInput
            v-model="form.name"
            :placeholder="$t('llm.namePlaceholder')"
            maxlength="64"
          />
        </ElFormItem>
        <ElFormItem :label="$t('llm.baseUrl')" prop="baseUrl">
          <ElInput
            v-model="form.baseUrl"
            :placeholder="$t('llm.baseUrlPlaceholder')"
            maxlength="255"
          />
        </ElFormItem>
        <ElFormItem :label="$t('llm.apiKey')">
          <ElInput
            v-model="form.apiKey"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="
              editingId == null ? $t('llm.apiKeyPlaceholder') : $t('llm.apiKeyEditPlaceholder')
            "
          />
        </ElFormItem>
        <ElFormItem :label="$t('llm.model')" prop="model">
          <ElInput
            v-model="form.model"
            :placeholder="$t('llm.modelPlaceholder')"
            maxlength="64"
          />
        </ElFormItem>
        <ElFormItem :label="$t('llm.temperature')">
          <ElInputNumber
            v-model="form.temperature"
            :min="0"
            :max="2"
            :step="0.1"
            :precision="1"
            controls-position="right"
            class="!w-40"
          />
        </ElFormItem>
        <ElFormItem :label="$t('llm.maxTokens')">
          <ElInputNumber
            v-model="form.maxTokens"
            :min="1"
            :step="256"
            controls-position="right"
            class="!w-40"
          />
        </ElFormItem>
        <ElFormItem :label="$t('llm.timeout')">
          <ElSelect v-model="form.timeoutSeconds" class="!w-40">
            <ElOption :label="30" :value="30" />
            <ElOption :label="60" :value="60" />
            <ElOption :label="120" :value="120" />
            <ElOption :label="180" :value="180" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="$t('llm.enabled')">
          <ElSwitch
            v-model="form.enabled"
            :active-text="$t('llm.enabledOn')"
            :inactive-text="$t('llm.enabledOff')"
          />
        </ElFormItem>
        <ElFormItem :label="$t('llm.remark')">
          <ElInput
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="255"
            :placeholder="$t('llm.remarkPlaceholder')"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <VbenButton variant="outline" size="sm" @click="dialogVisible = false">
          {{ $t('llm.cancel') }}
        </VbenButton>
        <VbenButton variant="default" size="sm" :disabled="saving" @click="save">
          {{ $t('llm.save') }}
        </VbenButton>
      </template>
    </el-dialog>
  </Page>
</template>
