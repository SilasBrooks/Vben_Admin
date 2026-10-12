<script lang="ts" setup>
import type { UploadRequestOptions } from 'element-plus';

import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SkillItem } from '#/api/system/skill';

import { ref } from 'vue';

import { Page, VbenButton, useVbenModal } from '@vben/common-ui';

import { ElMessage, ElMessageBox, ElTag, ElUpload } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteSkillApi,
  getSkillListApi,
  setSkillEnabledApi,
  uploadSkillPackageApi,
} from '#/api/system/skill';
import { $t } from '#/locales';

import SkillFiles from './skill-files.vue';
import SkillForm from './skill-form.vue';

// ------------------------------------------------------------------
// 新增/编辑弹窗（项目封装 useVbenModal，新增/编辑共用）
// ------------------------------------------------------------------

const [SkillFormModal, skillFormApi] = useVbenModal({
  connectedComponent: SkillForm,
});

function openAdd() {
  skillFormApi.setData({}).open();
}

function openEdit(row: SkillItem) {
  skillFormApi.setData({ ...row }).open();
}

function onFormSaved() {
  gridApi.reload();
}

// ------------------------------------------------------------------
// 包内文件弹窗（只读：清单 + 单文件下载 + 整包下载）
// ------------------------------------------------------------------

const [SkillFilesModal, skillFilesApi] = useVbenModal({
  connectedComponent: SkillFiles,
});

function openFiles(row: SkillItem) {
  skillFilesApi
    .setData({
      id: row.id,
      name: row.title || row.name,
      packageFileName: row.packageFileName,
    })
    .open();
}

// ------------------------------------------------------------------
// 技能包上传（ElUpload 托管文件选择 + http-request 自定义上传）
// ------------------------------------------------------------------

const uploading = ref(false);

async function onPackageUpload(options: UploadRequestOptions) {
  uploading.value = true;
  try {
    const skill = await uploadSkillPackageApi(options.file as File);
    ElMessage.success(
      $t('skill.uploadPackageSuccess', { name: skill.title || skill.name }),
    );
    gridApi.reload();
  } finally {
    uploading.value = false;
  }
}

// ------------------------------------------------------------------
// 列表
// ------------------------------------------------------------------

const gridOptions: VxeTableGridOptions<SkillItem> = {
  columns: [
    { type: 'seq', title: '#', width: 50 },
    {
      field: 'name',
      title: $t('skill.name'),
      minWidth: 150,
      showOverflow: true,
      slots: { default: 'name' },
    },
    { field: 'title', title: $t('skill.title'), minWidth: 120, showOverflow: true },
    {
      field: 'description',
      title: $t('skill.description'),
      minWidth: 240,
      showOverflow: true,
    },
    {
      field: 'builtIn',
      title: $t('skill.builtIn'),
      width: 90,
      slots: { default: 'builtIn' },
    },
    {
      field: 'enabled',
      title: $t('skill.status'),
      width: 90,
      slots: { default: 'status' },
    },
    { title: $t('skill.action'), width: 260, fixed: 'right', slots: { default: 'action' } },
  ],
  pagerConfig: { enabled: true },
  proxyConfig: {
    ajax: {
      query: async ({ page }, formValues) => {
        return await getSkillListApi({
          keyword: formValues?.keyword,
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
        componentProps: { placeholder: $t('skill.keywordPlaceholder') },
        fieldName: 'keyword',
        label: $t('skill.keyword'),
      },
    ],
  },
});

// ------------------------------------------------------------------
// 行操作
// ------------------------------------------------------------------

async function toggle(row: SkillItem) {
  const enable = row.enabled !== 1;
  await setSkillEnabledApi(row.id, enable);
  ElMessage.success(enable ? $t('skill.enableSuccess') : $t('skill.disableSuccess'));
  gridApi.reload();
}

async function remove(row: SkillItem) {
  await ElMessageBox.confirm(
    $t('skill.deleteConfirm', { name: row.title || row.name }),
    $t('skill.deleteTitle'),
    { type: 'warning' },
  );
  await deleteSkillApi(row.id);
  ElMessage.success($t('skill.deleteSuccess'));
  gridApi.reload();
}
</script>

<template>
  <Page auto-content-height>
    <SkillFormModal @saved="onFormSaved" />
    <SkillFilesModal />
    <Grid>
      <template #toolbar-actions>
        <ElUpload
          v-access:code="'System:Skill:Edit'"
          accept=".zip"
          :disabled="uploading"
          :http-request="onPackageUpload"
          :show-file-list="false"
        >
          <VbenButton variant="default" :loading="uploading">
            {{ uploading ? $t('skill.uploading') : $t('skill.uploadPackage') }}
          </VbenButton>
        </ElUpload>
        <VbenButton
          v-access:code="'System:Skill:Add'"
          variant="default"
          class="ml-2"
          @click="openAdd"
        >
          {{ $t('skill.add') }}
        </VbenButton>
      </template>

      <template #name="{ row }">
        <span>{{ (row as SkillItem).name }}</span>
        <ElTag
          v-if="(row as SkillItem).packageFileKey"
          class="ml-1"
          size="small"
        >
          {{ $t('skill.hasPackage') }}
        </ElTag>
      </template>

      <template #builtIn="{ row }">
        <ElTag
          :type="(row as SkillItem).builtIn === 1 ? 'warning' : 'info'"
          size="small"
        >
          {{
            (row as SkillItem).builtIn === 1 ? $t('skill.yes') : $t('skill.no')
          }}
        </ElTag>
      </template>

      <template #status="{ row }">
        <ElTag :type="(row as SkillItem).enabled === 1 ? 'success' : 'info'" size="small">
          {{
            (row as SkillItem).enabled === 1
              ? $t('skill.enabledOn')
              : $t('skill.enabledOff')
          }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <VbenButton
          v-access:code="'System:Skill:List'"
          variant="link"
          size="sm"
          :disabled="!(row as SkillItem).packageFileKey"
          @click="openFiles(row as SkillItem)"
        >
          {{ $t('skill.files') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Skill:Edit'"
          variant="link"
          size="sm"
          @click="openEdit(row as SkillItem)"
        >
          {{ $t('skill.edit') }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Skill:Edit'"
          variant="link"
          size="sm"
          @click="toggle(row as SkillItem)"
        >
          {{
            (row as SkillItem).enabled === 1
              ? $t('skill.disable')
              : $t('skill.enable')
          }}
        </VbenButton>
        <VbenButton
          v-access:code="'System:Skill:Delete'"
          variant="link"
          size="sm"
          class="text-destructive"
          :disabled="(row as SkillItem).builtIn === 1"
          @click="remove(row as SkillItem)"
        >
          {{ $t('skill.delete') }}
        </VbenButton>
      </template>
    </Grid>
  </Page>
</template>
