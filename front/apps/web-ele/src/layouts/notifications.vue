<script lang="ts" setup>
import type { NoticeItem } from '#/api/notice';

import { onBeforeUnmount, onMounted, ref } from 'vue';

import { useAppConfig } from '@vben/hooks';
import { createIconifyIcon } from '@vben/icons';
import { useAccessStore } from '@vben/stores';

import { ElBadge, ElButton, ElMessage, ElPopover, ElScrollbar } from 'element-plus';

import {
  getNoticeListApi,
  getNoticeUnreadCountApi,
  readAllNoticeApi,
  readNoticeApi,
} from '#/api/notice';
import { $t } from '#/locales';

const BellIcon = createIconifyIcon('lucide:bell');

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
const accessStore = useAccessStore();

const popoverVisible = ref(false);
const unreadCount = ref(0);
const items = ref<NoticeItem[]>([]);

const RECONNECT_DELAY = 30_000;

let reconnectTimer: null | ReturnType<typeof setTimeout> = null;
let socket: null | WebSocket = null;

function formatTime(value: string): string {
  return value.split('.')[0]?.replace('T', ' ') ?? value;
}

async function fetchUnreadCount() {
  try {
    const res = await getNoticeUnreadCountApi();
    unreadCount.value = res.count;
  } catch {
    // 失败细节由全局响应拦截器提示，这里静默
  }
}

async function fetchList() {
  try {
    const res = await getNoticeListApi(1, 10);
    items.value = res.items;
  } catch {
    // 失败细节由全局响应拦截器提示，这里静默
  }
}

async function handleReadItem(item: NoticeItem) {
  if (item.readFlag === 1) return;
  try {
    await readNoticeApi([item.id]);
    await Promise.all([fetchUnreadCount(), fetchList()]);
  } catch {
    // 失败细节由全局响应拦截器提示，这里静默
  }
}

async function handleReadAll() {
  try {
    await readAllNoticeApi();
    ElMessage.success($t('notice.readAllSuccess'));
    await Promise.all([fetchUnreadCount(), fetchList()]);
  } catch {
    // 失败细节由全局响应拦截器提示，这里静默
  }
}

/**
 * 由 apiURL 推导 WS 地址：与 REST 接口同一 context-path。
 * 例：apiURL=/api → ws(s)://<host>/api/ws/notice?token=xxx
 * （dev 经 vite /api 代理的 ws 转发、生产经 nginx /api/ 反代，最终到达后端 /ws/notice 端点）
 */
function buildWsUrl(): null | string {
  const token = accessStore.accessToken;
  if (!apiURL || !token) return null;
  const url = new URL(apiURL, window.location.href);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  url.pathname = `${url.pathname.replace(/\/+$/, '')}/ws/notice`;
  url.searchParams.set('token', token);
  return url.toString();
}

function connectSocket() {
  if (reconnectTimer || socket) return;
  const wsUrl = buildWsUrl();
  if (!wsUrl) return;
  socket = new WebSocket(wsUrl);
  socket.onmessage = (event) => {
    try {
      const frame = JSON.parse(String(event.data)) as { type?: string };
      if (frame.type === 'notice') {
        void fetchUnreadCount();
        void fetchList();
      }
    } catch {
      // 忽略无法解析的帧
    }
  };
  socket.onclose = () => {
    socket = null;
    scheduleReconnect();
  };
  socket.onerror = () => {
    socket?.close();
  };
}

function scheduleReconnect() {
  if (reconnectTimer) return;
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connectSocket();
  }, RECONNECT_DELAY);
}

onMounted(() => {
  void fetchUnreadCount();
  connectSocket();
});

onBeforeUnmount(() => {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  if (socket) {
    socket.onmessage = null;
    socket.onerror = null;
    socket.onclose = null;
    socket.close();
    socket = null;
  }
});
</script>

<template>
  <ElPopover
    v-model:visible="popoverVisible"
    :width="320"
    placement="bottom-end"
    trigger="click"
    @show="fetchList"
  >
    <template #reference>
      <button :aria-label="$t('notice.bellAria')" class="notice-bell" type="button">
        <ElBadge :hidden="unreadCount === 0" :max="99" :value="unreadCount">
          <BellIcon class="notice-bell__icon" />
        </ElBadge>
      </button>
    </template>

    <div class="notice-panel">
      <div class="notice-panel__header">{{ $t('notice.title') }}</div>
      <ElScrollbar max-height="320px">
        <div v-if="items.length === 0" class="notice-panel__empty">
          {{ $t('notice.empty') }}
        </div>
        <div
          v-for="item in items"
          :key="item.id"
          class="notice-item"
          :class="{ 'is-unread': item.readFlag === 0 }"
          @click="handleReadItem(item)"
        >
          <div class="notice-item__row">
            <span class="notice-item__title">{{ item.title }}</span>
            <span v-if="item.readFlag === 0" class="notice-item__dot" />
          </div>
          <div class="notice-item__content">{{ item.content }}</div>
          <div class="notice-item__time">{{ formatTime(item.createTime) }}</div>
        </div>
      </ElScrollbar>
      <div class="notice-panel__footer">
        <ElButton
          :disabled="unreadCount === 0"
          link
          size="small"
          type="primary"
          @click="handleReadAll"
        >
          {{ $t('notice.readAll') }}
        </ElButton>
      </div>
    </div>
  </ElPopover>
</template>

<style scoped>
.notice-bell {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  transition: background 0.2s;
}

.notice-bell:hover {
  background: var(--el-fill-color, rgb(0 0 0 / 6%));
}

.notice-bell__icon {
  width: 18px;
  height: 18px;
  font-size: 18px;
}

.notice-panel__header {
  padding-bottom: 8px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.notice-panel__empty {
  padding: 32px 0;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary, #909399);
}

.notice-item {
  padding: 8px 4px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}

.notice-item:hover {
  background: var(--el-fill-color-light, #f5f7fa);
}

.notice-item.is-unread {
  background: var(--el-color-primary-light-9, #ecf5ff);
}

.notice-item.is-unread:hover {
  background: var(--el-color-primary-light-8, #d9ecff);
}

.notice-item__row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.notice-item__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.notice-item__dot {
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-danger, #f56c6c);
}

.notice-item__content {
  overflow: hidden;
  margin-top: 2px;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--el-text-color-regular, #606266);
}

.notice-item__time {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.notice-panel__footer {
  display: flex;
  justify-content: center;
  padding-top: 8px;
  margin-top: 4px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
}
</style>
