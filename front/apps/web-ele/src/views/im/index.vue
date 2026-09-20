<script lang="ts" setup>
import type { ImConversation, ImMessage, ImPeer } from '#/api/im';

import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';

import { useAppConfig } from '@vben/hooks';
import { useAccessStore } from '@vben/stores';

import {
  ElBadge,
  ElButton,
  ElDialog,
  ElInput,
  ElOption,
  ElScrollbar,
  ElSelect,
} from 'element-plus';

import {
  getImConversationsApi,
  getImMessagesApi,
  getImPeersApi,
  readImMessagesApi,
  sendImMessageApi,
} from '#/api/im';
import { $t } from '#/locales';

/** WS 下行帧：chat 新消息 / read 对方已读回执 */
interface WsFrame {
  message?: ImMessage;
  peerId?: number;
  type?: string;
}

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
const accessStore = useAccessStore();

const PAGE_SIZE = 20;
const RECONNECT_DELAY = 30_000;
const AVATAR_COLORS = [
  '#409eff',
  '#67c23a',
  '#e6a23c',
  '#f56c6c',
  '#909399',
  '#9a6fe0',
  '#2bb3a3',
];

const conversations = ref<ImConversation[]>([]);
const activePeerId = ref<null | number>(null);
const messages = ref<ImMessage[]>([]);
const hasMore = ref(false);
const loadingMessages = ref(false);
const loadingMore = ref(false);
const sending = ref(false);
const input = ref('');
const keyword = ref('');
const peers = ref<ImPeer[]>([]);
const dialogVisible = ref(false);
const selectedPeerId = ref<null | number>(null);
const scrollbarRef = ref<InstanceType<typeof ElScrollbar>>();

const sortedConversations = computed(() => {
  const withLast = conversations.value.filter((c) => c.lastMessage);
  const without = conversations.value.filter((c) => !c.lastMessage);
  withLast.sort((a, b) => b.lastMessage!.id - a.lastMessage!.id);
  return [...withLast, ...without];
});

const filteredConversations = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) return sortedConversations.value;
  return sortedConversations.value.filter(
    (c) =>
      (c.peer.nickname ?? '').toLowerCase().includes(kw) ||
      c.peer.username.toLowerCase().includes(kw),
  );
});

const activePeer = computed(() => {
  return conversations.value.find((c) => c.peer.id === activePeerId.value)?.peer;
});

function peerName(peer: ImPeer): string {
  return peer.nickname || peer.username;
}

function avatarColor(id: number): string {
  return AVATAR_COLORS[id % AVATAR_COLORS.length]!;
}

function formatTime(value: string): string {
  return value.split('.')[0]?.replace('T', ' ') ?? value;
}

async function fetchConversations() {
  try {
    conversations.value = await getImConversationsApi();
  } catch {
    // 失败细节由全局响应拦截器提示，这里静默
  }
}

async function openDialog() {
  dialogVisible.value = true;
  try {
    peers.value = await getImPeersApi();
  } catch {
    // 失败细节由全局响应拦截器提示，这里静默
  }
}

async function confirmNewChat() {
  const peerId = selectedPeerId.value;
  if (!peerId) return;
  dialogVisible.value = false;
  selectedPeerId.value = null;
  if (!conversations.value.some((c) => c.peer.id === peerId)) {
    const peer = peers.value.find((p) => p.id === peerId);
    if (peer) {
      // 本地先建空会话（lastMessage 为空排在列表末尾），选中后由历史/新消息回填
      conversations.value.push({ lastMessage: null, peer, unreadCount: 0 });
    }
  }
  await selectConversation(peerId);
}

async function selectConversation(peerId: number) {
  activePeerId.value = peerId;
  loadingMessages.value = true;
  try {
    const items = await getImMessagesApi(peerId);
    messages.value = items;
    hasMore.value = items.length >= PAGE_SIZE;
    await markRead(peerId);
    await nextTick();
    scrollToBottom();
  } catch {
    // 失败细节由全局响应拦截器提示，这里静默
  } finally {
    loadingMessages.value = false;
  }
}

/** 标记对方发来的消息已读：服务端 + 本地列表同步 */
async function markRead(peerId: number) {
  try {
    await readImMessagesApi(peerId);
    const conv = conversations.value.find((c) => c.peer.id === peerId);
    if (conv) conv.unreadCount = 0;
    for (const m of messages.value) {
      if (m.senderId === peerId && m.readFlag === 0) m.readFlag = 1;
    }
  } catch {
    // 失败细节由全局响应拦截器提示，这里静默
  }
}

/** 向上翻页：beforeId 游标取更早消息，插入头部并保持视口位置 */
async function loadMore() {
  const peerId = activePeerId.value;
  if (!peerId || loadingMore.value || !hasMore.value || messages.value.length === 0) {
    return;
  }
  loadingMore.value = true;
  const wrap = scrollbarRef.value?.wrapRef;
  const prevHeight = wrap?.scrollHeight ?? 0;
  try {
    const items = await getImMessagesApi(peerId, messages.value[0]?.id);
    messages.value = [...items, ...messages.value];
    hasMore.value = items.length >= PAGE_SIZE;
    await nextTick();
    if (wrap) wrap.scrollTop = wrap.scrollHeight - prevHeight;
  } catch {
    // 失败细节由全局响应拦截器提示，这里静默
  } finally {
    loadingMore.value = false;
  }
}

async function handleSend() {
  const peerId = activePeerId.value;
  const content = input.value.trim();
  if (!peerId || !content || sending.value) return;
  sending.value = true;
  try {
    const { message } = await sendImMessageApi(peerId, content);
    messages.value.push(message);
    input.value = '';
    updateLastMessage(peerId, message);
    await nextTick();
    scrollToBottom();
  } catch {
    // 失败细节由全局响应拦截器提示，这里静默
  } finally {
    sending.value = false;
  }
}

/** 回填会话最后一条消息（会话不存在时重拉列表，例如对方主动开聊） */
function updateLastMessage(peerId: number, message: ImMessage) {
  const conv = conversations.value.find((c) => c.peer.id === peerId);
  if (!conv) {
    void fetchConversations();
    return;
  }
  conv.lastMessage = {
    content: message.content,
    createTime: message.createTime,
    id: message.id,
    senderId: message.senderId,
  };
}

function scrollToBottom() {
  const wrap = scrollbarRef.value?.wrapRef;
  if (wrap) wrap.scrollTop = wrap.scrollHeight;
}

function handleInputKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    void handleSend();
  }
}

// ---------------- WebSocket 实时通道（/api/ws/im） ----------------

let reconnectTimer: null | ReturnType<typeof setTimeout> = null;
let socket: null | WebSocket = null;

/**
 * 由 apiURL 推导 WS 地址：与 REST 接口同一 context-path。
 * 例：apiURL=/api → ws(s)://<host>/api/ws/im?token=xxx
 */
function buildWsUrl(): null | string {
  const token = accessStore.accessToken;
  if (!apiURL || !token) return null;
  const url = new URL(apiURL, window.location.href);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  url.pathname = `${url.pathname.replace(/\/+$/, '')}/ws/im`;
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
      const frame = JSON.parse(String(event.data)) as WsFrame;
      if (frame.type === 'chat' && frame.message) {
        handleChatFrame(frame.message);
      } else if (frame.type === 'read' && frame.peerId) {
        handleReadFrame(frame.peerId);
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

/** 收到新消息：正在看该会话则追加并回已读，否则未读 +1 */
function handleChatFrame(message: ImMessage) {
  const senderId = message.senderId;
  if (senderId === activePeerId.value) {
    messages.value.push(message);
    void nextTick(scrollToBottom);
    void markRead(senderId);
  } else {
    const conv = conversations.value.find((c) => c.peer.id === senderId);
    if (conv) {
      conv.unreadCount += 1;
    } else {
      void fetchConversations();
    }
  }
  updateLastMessage(senderId, message);
}

/** 对方已读回执：把当前会话中我发给对方的消息置为已读 */
function handleReadFrame(readerId: number) {
  for (const m of messages.value) {
    if (m.receiverId === readerId && m.readFlag === 0) m.readFlag = 1;
  }
}

onMounted(() => {
  void fetchConversations();
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
  <div class="im-page">
    <!-- 左侧：联系人会话列表 -->
    <aside class="im-side">
      <div class="im-side__header">
        <span class="im-side__title">{{ $t('im.title') }}</span>
        <ElButton size="small" type="primary" @click="openDialog">
          {{ $t('im.newChat') }}
        </ElButton>
      </div>
      <div class="im-side__search">
        <ElInput
          v-model="keyword"
          :placeholder="$t('im.searchPlaceholder')"
          clearable
          size="small"
        />
      </div>
      <ElScrollbar class="im-side__list">
        <div v-if="filteredConversations.length === 0" class="im-side__empty">
          {{ $t('im.emptyConversations') }}
        </div>
        <div
          v-for="conv in filteredConversations"
          :key="conv.peer.id"
          class="im-conv"
          :class="{ 'is-active': conv.peer.id === activePeerId }"
          @click="selectConversation(conv.peer.id)"
        >
          <span
            class="im-avatar"
            :style="{ background: avatarColor(conv.peer.id) }"
          >
            {{ peerName(conv.peer).charAt(0).toUpperCase() }}
          </span>
          <span class="im-conv__body">
            <span class="im-conv__row">
              <span class="im-conv__name">{{ peerName(conv.peer) }}</span>
              <span
                v-if="conv.lastMessage"
                class="im-conv__time"
              >
                {{ formatTime(conv.lastMessage.createTime) }}
              </span>
            </span>
            <span class="im-conv__row">
              <span class="im-conv__preview">
                {{ conv.lastMessage?.content ?? '' }}
              </span>
              <ElBadge
                :hidden="conv.unreadCount === 0"
                :max="99"
                :value="conv.unreadCount"
              />
            </span>
          </span>
        </div>
      </ElScrollbar>
    </aside>

    <!-- 右侧：聊天窗 -->
    <section class="im-main">
      <template v-if="activePeer">
        <header class="im-main__header">
          {{ peerName(activePeer) }}
          <span class="im-main__username">@{{ activePeer.username }}</span>
        </header>

        <ElScrollbar ref="scrollbarRef" class="im-msgs">
          <div class="im-msgs__inner">
            <div class="im-msgs__more">
              <ElButton
                v-if="hasMore"
                :loading="loadingMore"
                link
                size="small"
                type="primary"
                @click="loadMore"
              >
                {{ $t('im.loadMore') }}
              </ElButton>
              <span v-else-if="messages.length > 0" class="im-msgs__nomore">
                {{ $t('im.noMore') }}
              </span>
            </div>
            <div v-if="messages.length === 0 && !loadingMessages" class="im-msgs__empty">
              {{ $t('im.emptyMessages') }}
            </div>
            <div
              v-for="m in messages"
              :key="m.id"
              class="im-msg"
              :class="{ 'is-own': m.senderId === activePeer.id }"
            >
              <div class="im-msg__bubble">{{ m.content }}</div>
              <div class="im-msg__meta">
                {{ formatTime(m.createTime) }}
                <template v-if="m.senderId === activePeer.id">
                  · {{ m.readFlag === 1 ? $t('im.read') : $t('im.unread') }}
                </template>
              </div>
            </div>
          </div>
        </ElScrollbar>

        <footer class="im-input" @keydown="handleInputKeydown">
          <ElInput
            v-model="input"
            :autosize="{ maxRows: 5, minRows: 2 }"
            :placeholder="$t('im.inputPlaceholder')"
            type="textarea"
            resize="none"
          />
          <ElButton
            :disabled="!input.trim()"
            :loading="sending"
            class="im-input__send"
            type="primary"
            @click="handleSend"
          >
            {{ $t('im.send') }}
          </ElButton>
        </footer>
      </template>
      <div v-else class="im-main__empty">{{ $t('im.selectConversation') }}</div>
    </section>

    <!-- 发起聊天 -->
    <ElDialog
      v-model="dialogVisible"
      :title="$t('im.newChat')"
      :width="380"
      append-to-body
    >
      <ElSelect
        v-model="selectedPeerId"
        :placeholder="$t('im.selectPeer')"
        filterable
        style="width: 100%"
      >
        <ElOption
          v-for="p in peers"
          :key="p.id"
          :label="`${peerName(p)} (@${p.username})`"
          :value="p.id"
        />
      </ElSelect>
      <template #footer>
        <ElButton @click="dialogVisible = false">
          {{ $t('common.cancel') }}
        </ElButton>
        <ElButton
          :disabled="!selectedPeerId"
          type="primary"
          @click="confirmNewChat"
        >
          {{ $t('common.confirm') }}
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.im-page {
  display: flex;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 8px;
  background: var(--el-bg-color, #fff);
}

.im-side {
  display: flex;
  flex-direction: column;
  width: 300px;
  min-width: 0;
  border-right: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.im-side__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px 8px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.im-side__search {
  padding: 0 14px 10px;
}

.im-side__list {
  flex: 1;
  min-height: 0;
}

.im-side__empty {
  padding: 32px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary, #909399);
}

.im-conv {
  display: flex;
  gap: 10px;
  padding: 10px 14px;
  cursor: pointer;
  transition: background 0.2s;
}

.im-conv:hover {
  background: var(--el-fill-color-light, #f5f7fa);
}

.im-conv.is-active {
  background: var(--el-color-primary-light-9, #ecf5ff);
}

.im-avatar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  user-select: none;
}

.im-conv__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.im-conv__row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.im-conv__name {
  flex: 1;
  overflow: hidden;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.im-conv__time {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.im-conv__preview {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  color: var(--el-text-color-secondary, #909399);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.im-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.im-main__header {
  flex-shrink: 0;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.im-main__username {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary, #909399);
}

.im-msgs {
  flex: 1;
  min-height: 0;
}

.im-msgs__inner {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 16px;
}

.im-msgs__more {
  display: flex;
  justify-content: center;
}

.im-msgs__nomore,
.im-msgs__empty {
  text-align: center;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.im-msgs__empty {
  padding: 32px 0;
  font-size: 13px;
}

.im-msg {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: 70%;
}

.im-msg.is-own {
  align-self: flex-end;
  align-items: flex-end;
}

.im-msg__bubble {
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--el-fill-color, #f4f4f5);
  font-size: 14px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--el-text-color-primary, #303133);
}

.im-msg.is-own .im-msg__bubble {
  background: var(--el-color-primary, #409eff);
  color: #fff;
}

.im-msg__meta {
  margin-top: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary, #909399);
}

.im-input {
  display: flex;
  flex-shrink: 0;
  gap: 10px;
  align-items: flex-end;
  padding: 10px 14px;
  border-top: 1px solid var(--el-border-color-lighter, #ebeef5);
}

.im-input__send {
  flex-shrink: 0;
}

.im-main__empty {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: var(--el-text-color-secondary, #909399);
}
</style>
