<script lang="ts" setup>
import type { NoticeItem } from '#/api/notice';

import { onActivated, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ElButton, ElCard, ElMessage, ElPagination, ElRadioGroup, ElRadioButton, ElTable, ElTableColumn, ElTag } from 'element-plus';

import {
  getNoticeListApi,
  readAllNoticeApi,
  readNoticeApi,
} from '#/api/notice';
import { $t } from '#/locales';

/** 类型筛选：'' 全部 / announcement 公告 / security 通知（支持铃铛公告入口 ?type= 定位） */
const route = useRoute();
const filterType = ref('');
const loading = ref(false);
const pageNum = ref(1);
const pageSize = ref(10);
const total = ref(0);
const items = ref<NoticeItem[]>([]);

function formatTime(value: string): string {
  return value.split('.')[0]?.replace('T', ' ') ?? value;
}

function isAnnouncement(item: NoticeItem): boolean {
  return item.msgType === 'announcement';
}

async function fetchList() {
  loading.value = true;
  try {
    const res = await getNoticeListApi(pageNum.value, pageSize.value, filterType.value || undefined);
    items.value = res.items;
    total.value = res.total;
  } finally {
    loading.value = false;
  }
}

function handleFilterChange() {
  pageNum.value = 1;
  void fetchList();
}

function handlePageChange() {
  void fetchList();
}

/** 标记单条已读并广播铃铛刷新 */
async function handleReadItem(item: NoticeItem) {
  if (item.readFlag === 1) return;
  await readNoticeApi([item.id]);
  ElMessage.success($t('notice.readSuccess'));
  window.dispatchEvent(new Event('notice:refresh'));
  await fetchList();
}

/** 全部已读并广播铃铛刷新 */
async function handleReadAll() {
  await readAllNoticeApi();
  ElMessage.success($t('notice.readAllSuccess'));
  window.dispatchEvent(new Event('notice:refresh'));
  await fetchList();
}

/**
 * 同步 URL query 并加载列表。
 * 页面为 hideInTab 隐藏路由（不在 KeepAlive 内）：onActivated 不会触发，
 * 必须靠 onMounted 首载 + watch 响应铃铛入口带 ?type= 的跳转。
 */
function syncFromQuery() {
  const type = String(route.query.type ?? '');
  filterType.value = type;
  pageNum.value = 1;
  void fetchList();
}

onMounted(syncFromQuery);

onActivated(syncFromQuery);

watch(
  () => route.query.type,
  () => {
    if (route.path === '/notice-center') {
      syncFromQuery();
    }
  },
);
</script>

<template>
  <ElCard class="notice-center" shadow="never">
    <div class="notice-center__toolbar">
      <ElRadioGroup v-model="filterType" @change="handleFilterChange">
        <ElRadioButton value="">{{ $t('notice.filterAll') }}</ElRadioButton>
        <ElRadioButton value="announcement">{{ $t('notice.typeAnnouncement') }}</ElRadioButton>
        <ElRadioButton value="security">{{ $t('notice.typeNotice') }}</ElRadioButton>
      </ElRadioGroup>
      <ElButton :disabled="total === 0" type="primary" @click="handleReadAll">
        {{ $t('notice.readAll') }}
      </ElButton>
    </div>

    <ElTable v-loading="loading" :data="items" :row-class-name="({ row }) => (row.readFlag === 0 ? 'is-unread-row' : '')">
      <ElTableColumn :label="$t('notice.colType')" width="90">
        <template #default="{ row }">
          <ElTag :type="isAnnouncement(row) ? 'warning' : 'primary'" effect="light" size="small">
            {{ isAnnouncement(row) ? $t('notice.typeAnnouncement') : $t('notice.typeNotice') }}
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn :label="$t('notice.colTitle')" min-width="220" prop="title" show-overflow-tooltip />
      <ElTableColumn :label="$t('notice.colContent')" min-width="320" prop="content" show-overflow-tooltip />
      <ElTableColumn :label="$t('notice.colTime')" width="180">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </ElTableColumn>
      <ElTableColumn :label="$t('notice.colStatus')" width="90">
        <template #default="{ row }">
          <ElTag v-if="row.readFlag === 0" size="small" type="danger">{{ $t('notice.unread') }}</ElTag>
          <span v-else class="notice-center__read">{{ $t('notice.read') }}</span>
        </template>
      </ElTableColumn>
      <ElTableColumn :label="$t('notice.colAction')" width="100">
        <template #default="{ row }">
          <ElButton v-if="row.readFlag === 0" link size="small" type="primary" @click="handleReadItem(row)">
            {{ $t('notice.markRead') }}
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <div class="notice-center__pager">
      <ElPagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        background
        layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleFilterChange"
      />
    </div>
  </ElCard>
</template>

<style scoped>
.notice-center__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.notice-center__read {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.notice-center__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

:deep(.is-unread-row) {
  font-weight: 600;
}
</style>
