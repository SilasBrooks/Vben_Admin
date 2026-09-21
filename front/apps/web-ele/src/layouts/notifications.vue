<script lang="ts" setup>
import type { NoticeItem } from '#/api/notice';
import type { ImConversation } from '#/api/im';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAppConfig } from '@vben/hooks';
import { createIconifyIcon } from '@vben/icons';
import { useAccessStore } from '@vben/stores';

import { ElBadge, ElButton, ElMessage, ElPopover, ElScrollbar, ElTag } from 'element-plus';

import {
  getNoticeListApi,
  getNoticeUnreadCountApi,
  readAllNoticeApi,
  readNoticeApi,
} from '#/api/notice';
import { getImConversationsApi } from '#/api/im';
import { $t } from '#/locales';

const BellIcon = createIconifyIcon('lucide:bell');
const ChatIcon = createIconifyIcon('lucide:message-circle');

const router = useRouter();
const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
const accessStore = useAccessStore();

const popoverVisible = ref(false);
const unreadCount = ref(0);
const items = ref<NoticeItem[]>([]);
const conversations = ref<ImConversation[]>([]);

/** 聊天浮层最多展示的会话数 */
const IM_PREVIEW_LIMIT = 3;
/** 通知/公告浮层最多展示的条数 */
const NOTICE_PREVIEW_LIMIT = 5;

const RECONNECT_DELAY = 30_000;

const imChats = computed(() =>
  conversations.value
    .filter((c) => c.lastMessage)
    .slice(0, IM_PREVIEW_LIMIT),
);
const imUnread = computed(() =>
  conversations.value.reduce((sum, c) => sum + (c.unreadCount ?? 0), 0),
);
const totalUnread = computed(() => unreadCount.value + imUnread.value);

let reconnectTimer: null | ReturnType<typeof setTimeout> = null;
let noticeSocket: null | WebSocket = null;
let imSocket: null | WebSocket = null;

function formatTime(value: string): string {
  return value.split('.')[0]?.replace('T', ' ') ?? value;
}

function isAnnouncement(item: NoticeItem): boolean {
  return item.msgType === 'announcement';
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
    const res = await getNoticeListApi(1, NOTICE_PREVIEW_LIMIT);
    items.value = res.items;
  } catch {
    // 失败细节由全局响应拦截器提示，这里静默
  }
}

async function fetchImConversations() {
  try {
    conversations.value = await getImConversationsApi();
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

/** 跳消息聊天页并定位到该会话 */
function goImPeer(peerId: number) {
  popoverVisible.value = false;
  router.push({ path: '/im', query: { peer: peerId } });
}

/** 跳消息中心并定位公告筛选 */
function goAnnouncements() {
  popoverVisible.value = false;
  router.push({ path: '/notice-center', query: { type: 'announcement' } });
}

/** 跳消息中心全量列表 */
function goCenter() {
  popoverVisible.value = false;
  router.push({ path: '/notice-center' });
}

/**
 * 由 apiURL 推导 WS 地址：与 REST 接口同一 context-path。
 * 例：apiURL=/api → ws(s)://<host>/api/ws/notice?token=xxx
 * （dev 经 vite /api 代理的 ws 转发、生产经 nginx /api/ 反代，最终到达后端端点；
 * 后端 handler 按 userId 持有 Set<Session>，铃铛与业务页各持一条连接互不顶替）
 */
function buildWsUrl(path: string): null | string {
  const token = accessStore.accessToken;
  if (!apiURL || !token) return null;
  const url = new URL(apiURL, window.location.href);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  url.pathname = `${url.pathname.replace(/\/+$/, '')}${path}`;
  url.searchParams.set('token', token);
  return url.toString();
}

function openSocket(path: string, onFrame: (frame: Record<string, any>) => void) {
  const socket = new WebSocket(buildWsUrl(path)!);
  socket.onmessage = (event) => {
    try {
      onFrame(JSON.parse(String(event.data)) as Record<string, any>);
    } catch {
      // 忽略无法解析的帧
    }
  };
  socket.onclose = () => {
    scheduleReconnect(path, onFrame);
    return null;
  };
  socket.onerror = () => {
    socket?.close();
  };
  return socket;
}

function connectSockets() {
  if (reconnectTimer) return;
  if (!noticeSocket && buildWsUrl('/ws/notice')) {
    noticeSocket = openSocket('/ws/notice', () => {
      void fetchUnreadCount();
      if (popoverVisible.value) void fetchList();
    });
    noticeSocket.onclose = () => {
      noticeSocket = null;
      scheduleReconnect('/ws/notice', noticeFrameHandler);
    };
  }
  if (!imSocket && buildWsUrl('/ws/im')) {
    imSocket = openSocket('/ws/im', (frame) => {
      // chat=新消息 / read=我发的消息被读（影响会话列表最新消息时间序）
      if (frame.type === 'chat' || frame.type === 'read') {
        void fetchImConversations();
      }
    });
    imSocket.onclose = () => {
      imSocket = null;
      scheduleReconnect('/ws/im', imFrameHandler);
    };
  }
}

function noticeFrameHandler(_frame: Record<string, any>) {
  void fetchUnreadCount();
  if (popoverVisible.value) void fetchList();
}

function imFrameHandler(_frame: Record<string, any>) {
  void fetchImConversations();
}

function scheduleReconnect(path: string, handler: (frame: Record<string, any>) => void) {
  if (reconnectTimer) return;
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    if (path === '/ws/notice') {
      if (!noticeSocket && buildWsUrl(path)) {
        noticeSocket = openSocket(path, handler);
        noticeSocket.onclose = () => {
          noticeSocket = null;
          scheduleReconnect(path, handler);
        };
      }
    } else if (!imSocket && buildWsUrl(path)) {
      imSocket = openSocket(path, handler);
      imSocket.onclose = () => {
        imSocket = null;
        scheduleReconnect(path, handler);
      };
    }
  }, RECONNECT_DELAY);
}

/** 通知中心/IM 页操作后广播刷新，保持铃铛聚合数据同步 */
function onNoticeRefresh() {
  void fetchUnreadCount();
  void fetchImConversations();
  if (popoverVisible.value) void fetchList();
}

onMounted(() => {
  void fetchUnreadCount();
  void fetchImConversations();
  connectSockets();
  window.addEventListener('notice:refresh', onNoticeRefresh);
});

onBeforeUnmount(() => {
  window.removeEventListener('notice:refresh', onNoticeRefresh);
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  for (const socket of [noticeSocket, imSocket]) {
    if (socket) {
      socket.onmessage = null;
      socket.onerror = null;
      socket.onclose = null;
      socket.close();
    }
  }
  noticeSocket = null;
  imSocket = null;
});
</script>

<template>
  <ElPopover
    v-model:visible="popoverVisible"
    :show-after="100"
    :width="360"
    placement="bottom-end"
    trigger="hover"
    @show="() => { fetchList(); fetchImConversations(); }"
  >
    <template #reference>
      <button :aria-label="$t('notice.bellAria')" class="notice-bell" type="button">
        <ElBadge :hidden="totalUnread === 0" :max="99" :value="totalUnread">
          <BellIcon class="notice-bell__icon" />
        </ElBadge>
      </button>
    </template>

    <div class="notice-panel">
      <!-- 聊天消息区：点击进入会话 -->
      <div class="notice-panel__section">
        <div class="notice-panel__header">
          <span class="notice-panel__header-title">
            <ChatIcon class="notice-panel__header-icon" />
            {{ $t('notice.chatSection') }}
          </span>
          <ElTag v-if="imUnread > 0" size="small" type="danger" effect="light" round>
            {{ imUnread }}
          </ElTag>
        </div>
        <div
          v-for="chat in imChats"
          :key="chat.peer.id"
          class="notice-item notice-item--chat"
          @click="goImPeer(chat.peer.id)"
        >
          <span class="notice-item__avatar">{{ (chat.peer.nickname || chat.peer.username).slice(0, 1) }}</span>
          <span class="notice-item__body">
            <span class="notice-item__row">
              <span class="notice-item__title">{{ chat.peer.nickname || chat.peer.username }}</span>
              <span class="notice-item__time">{{ chat.lastMessage ? formatTime(chat.lastMessage.createTime) : '' }}</span>
            </span>
            <span class="notice-item__content">{{ chat.lastMessage?.content }}</span>
          </span>
          <span v-if="chat.unreadCount > 0" class="notice-item__badge">{{ chat.unreadCount > 99 ? '99+' : chat.unreadCount }}</span>
        </div>
        <div v-if="imChats.length === 0" class="notice-panel__empty notice-panel__empty--small">
          {{ $t('notice.chatEmpty') }}
        </div>
      </div>

      <!-- 通知/公告区：公告跳筛选列表，通知点击标记已读 -->
      <div class="notice-panel__section">
        <div class="notice-panel__header">
          <span>{{ $t('notice.title') }}</span>
          <span class="notice-panel__header-actions">
            <ElButton link size="small" type="warning" @click="goAnnouncements">
              {{ $t('notice.typeAnnouncement') }}
            </ElButton>
            <ElButton link size="small" type="primary" @click="goCenter">
              {{ $t('notice.viewAll') }}
            </ElButton>
          </span>
        </div>
        <ElScrollbar max-height="280px">
          <div v-if="items.length === 0" class="notice-panel__empty">
            {{ $t('notice.empty') }}
          </div>
          <div
            v-for="item in items"
            :key="item.id"
            class="notice-item"
            :class="{ 'is-unread': item.readFlag === 0 }"
            @click="isAnnouncement(item) ? goAnnouncements() : handleReadItem(item)"
          >
            <div class="notice-item__row">
              <ElTag
                :type="isAnnouncement(item) ? 'warning' : 'primary'"
                effect="light"
                size="small"
              >
                {{ isAnnouncement(item) ? $t('notice.typeAnnouncement') : $t('notice.typeNotice') }}
              </ElTag>
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

.notice-panel__section + .notice-panel__section {
  margin-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
  padding-top: 4px;
}

.notice-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 6px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.notice-panel__header-title {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.notice-panel__header-icon {
  width: 14px;
  height: 14px;
}

.notice-panel__header-actions {
  display: inline-flex;
  align-items: center;
}

.notice-panel__empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary, #909399);
}

.notice-panel__empty--small {
  padding: 12px 0;
}

.notice-item {
  display: flex;
  gap: 8px;
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

.notice-item--chat {
  align-items: center;
}

.notice-item__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--el-color-primary-light-7, #c6e2ff);
  color: var(--el-color-primary, #409eff);
  font-weight: 600;
}

.notice-item__body {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
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

.notice-item__badge {
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: 10px;
  background: var(--el-color-danger, #f56c6c);
  font-size: 12px;
  color: #fff;
  line-height: 18px;
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
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.notice-panel__footer {
  display: flex;
  justify-content: center;
  padding-top: 6px;
  margin-top: 2px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
}
</style>
