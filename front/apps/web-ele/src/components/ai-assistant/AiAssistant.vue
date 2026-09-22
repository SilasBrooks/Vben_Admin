<script lang="ts" setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';

import { createIconifyIcon } from '@vben/icons';

import { $t } from '#/locales';

import { useAiChat } from './use-ai-chat';
import PlanCard from './PlanCard.vue';
import { formatCardArgs } from './tool-labels';

// 图标（lucide 集）
const TrashIcon = createIconifyIcon('lucide:trash-2');
const CloseIcon = createIconifyIcon('lucide:x');
const SendIcon = createIconifyIcon('lucide:arrow-up');
const StopIcon = createIconifyIcon('lucide:square');

const {
  messages,
  loading,
  send,
  stop,
  clear,
  confirmCard,
  cancelCard,
  confirmPlan,
  cancelPlan,
  confirmDangerStep,
  cancelDangerStep,
} = useAiChat();

const open = ref(false);
const inputText = ref('');
const scrollRef = ref<HTMLElement>();

// 计时驱动：loading 期间每秒刷新一次，驱动「已处理 Ns」更新
const now = ref(Date.now());
let timer: ReturnType<typeof setInterval> | null = null;
watch(
  loading,
  (v) => {
    if (v) {
      now.value = Date.now();
      timer = setInterval(() => {
        now.value = Date.now();
      }, 1000);
    } else if (timer) {
      clearInterval(timer);
      timer = null;
    }
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});

function formatElapsed(start?: number): string {
  if (!start) return '';
  const sec = Math.max(0, Math.floor((now.value - start) / 1000));
  if (sec < 60) return `${sec}s`;
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${m}m ${s}s`;
}

const suggestions = computed(() => [
  $t('ai.chat.suggestionDepartments'),
  $t('ai.chat.suggestionCreateRole'),
  $t('ai.chat.suggestionUsers'),
]);

function toggle() {
  open.value = !open.value;
  if (open.value) {
    void nextTick(scrollToBottom);
  }
}

async function handleSend() {
  const text = inputText.value.trim();
  if (!text || loading.value) return;
  inputText.value = '';
  await send(text);
  void nextTick(scrollToBottom);
}

async function handleSuggestion(text: string) {
  if (loading.value) return;
  inputText.value = '';
  await send(text);
  void nextTick(scrollToBottom);
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    handleSend();
  }
}

function scrollToBottom() {
  const el = scrollRef.value;
  if (el) {
    el.scrollTop = el.scrollHeight;
  }
}

// 消息列表变化时自动滚到底
watch(
  () => messages.value.length,
  () => void nextTick(scrollToBottom),
);
// 流式输出期间持续滚到底
watch(messages, () => void nextTick(scrollToBottom), { deep: true });
</script>

<template>
  <!-- 悬浮入口按钮 -->
  <div
    class="ai-fab"
    :class="{ active: open }"
    role="button"
    tabindex="0"
    :aria-label="open ? $t('ai.chat.closeAria') : $t('ai.chat.openAria')"
    @click="toggle"
    @keydown.enter="toggle"
  >
    <img src="/logo.png" :alt="$t('ai.chat.title')" class="ai-fab__img" />
    <span v-if="loading" class="ai-fab__dot" />
  </div>

  <!-- 聊天面板 -->
  <Transition name="ai-slide">
    <section v-if="open" class="ai-panel">
      <!-- 标题栏 -->
      <header class="ai-panel__header">
        <div class="ai-panel__title">
          <img src="/logo.png" alt="" class="ai-panel__logo" />
          <span>{{ $t('ai.chat.title') }}</span>
        </div>
        <div class="ai-panel__actions">
          <button
            class="ai-icon-btn"
            :title="$t('ai.chat.clearSession')"
            :disabled="loading"
            @click="clear"
          >
            <TrashIcon :size="18" />
          </button>
          <button class="ai-icon-btn" :title="$t('ai.chat.close')" @click="toggle">
            <CloseIcon :size="18" />
          </button>
        </div>
      </header>

      <!-- 消息区 -->
      <div ref="scrollRef" class="ai-panel__body">
        <!-- 空状态 -->
        <div v-if="messages.length === 0" class="ai-empty">
          <div class="ai-empty__avatar">
            <img src="/logo.png" alt="" />
          </div>
          <p class="ai-empty__welcome">{{ $t('ai.chat.welcome') }}</p>
          <div class="ai-empty__chips">
            <button
              v-for="s in suggestions"
              :key="s"
              class="ai-chip"
              :disabled="loading"
              @click="handleSuggestion(s)"
            >
              {{ s }}
            </button>
          </div>
        </div>

        <!-- 消息列表 -->
        <template v-for="msg in messages" :key="msg.id">
          <!-- tool 消息不渲染气泡 -->
          <div v-if="msg.role === 'user'" class="ai-row ai-row--user">
            <div class="ai-bubble ai-bubble--user">{{ msg.content }}</div>
          </div>

          <div v-else-if="msg.role === 'assistant'" class="ai-row ai-row--bot">
            <img src="/logo.png" alt="" class="ai-avatar" />
            <div class="ai-bubble ai-bubble--bot" :class="{ 'is-error': msg.isError }">
              <!-- 思考中指示器 -->
              <div v-if="msg.thinking" class="ai-thinking">
                <span class="ai-thinking__dots">
                  <i /><i /><i />
                </span>
                <span class="ai-thinking__label">{{ $t('ai.chat.thinking') }}</span>
                <span class="ai-thinking__elapsed" v-if="formatElapsed(msg.thinkingStart)">
                  · {{ formatElapsed(msg.thinkingStart) }}
                </span>
              </div>

              <span v-else-if="msg.content" class="ai-bubble__text">{{ msg.content }}</span>

              <!-- 计划卡（多步任务） -->
              <PlanCard
                v-for="plan in msg.planCards"
                :key="plan.toolCallId"
                :plan="plan"
                @confirm="confirmPlan(plan)"
                @cancel="cancelPlan(plan)"
                @danger-confirm="confirmDangerStep(plan)"
                @danger-cancel="cancelDangerStep(plan)"
              />

              <!-- 确认卡片 -->
              <div v-for="card in msg.cards" :key="card.toolCallId" class="ai-card">
                <div class="ai-card__title">
                  <span class="ai-card__title-icon">📋</span>
                  <span>{{ card.title }}</span>
                </div>
                <table class="ai-card__table">
                  <tbody>
                    <tr
                      v-for="[label, value] in formatCardArgs(card.toolName, card.args)"
                      :key="label"
                    >
                      <td class="ai-card__label">{{ label }}</td>
                      <td class="ai-card__value">{{ value }}</td>
                    </tr>
                  </tbody>
                </table>

                <!-- 状态展示 -->
                <div v-if="card.status === 'pending'" class="ai-card__actions">
                  <button class="ai-card__btn ai-card__btn--primary" @click="confirmCard(card)">
                    {{ $t('ai.card.confirmExecute') }}
                  </button>
                  <button class="ai-card__btn" @click="cancelCard(card)">
                    {{ $t('ai.card.cancel') }}
                  </button>
                </div>

                <div v-else-if="card.status === 'executing'" class="ai-card__status">
                  <span class="ai-spinner" /> {{ $t('ai.card.executing') }}
                </div>

                <div v-else-if="card.status === 'done'" class="ai-card__status ai-card__status--ok">
                  ✅ {{ card.summary }}
                </div>

                <div v-else-if="card.status === 'cancelled'" class="ai-card__status">
                  {{ $t('ai.card.cancelled') }}
                </div>

                <div
                  v-else-if="card.status === 'error'"
                  class="ai-card__status ai-card__status--err"
                >
                  ❌ {{ card.errorMsg }}
                </div>
              </div>

              <!-- 流式光标 -->
              <span v-if="msg.streaming && !msg.thinking" class="ai-cursor" />
            </div>
          </div>
        </template>
      </div>

      <!-- 输入区 -->
      <footer class="ai-panel__footer">
        <textarea
          v-model="inputText"
          class="ai-input"
          :placeholder="$t('ai.chat.placeholder')"
          rows="1"
          :disabled="loading"
          @keydown="handleKeydown"
        />
        <div class="ai-panel__send">
          <button
            v-if="loading"
            class="ai-send ai-send--stop"
            :title="$t('ai.chat.stop')"
            @click="stop"
          >
            <StopIcon :size="18" />
          </button>
          <button
            v-else
            class="ai-send"
            :title="$t('ai.chat.send')"
            :disabled="!inputText.trim()"
            @click="handleSend"
          >
            <SendIcon :size="18" />
          </button>
        </div>
      </footer>
    </section>
  </Transition>
</template>

<style scoped>
.ai-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  cursor: pointer;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  box-shadow: 0 4px 16px rgb(99 102 241 / 40%);
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}
.ai-fab:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 20px rgb(99 102 241 / 50%);
}
.ai-fab.active {
  transform: scale(0.9);
}
.ai-fab__img {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
}
.ai-fab__dot {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #f56c6c;
  animation: ai-breathe 1.5s ease-in-out infinite;
}
@keyframes ai-breathe {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.6;
    transform: scale(0.8);
  }
}

.ai-panel {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 400px;
  max-height: min(640px, 82vh);
  display: flex;
  flex-direction: column;
  z-index: 9999;
  border-radius: 16px;
  overflow: hidden;
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  box-shadow: 0 12px 40px rgb(0 0 0 / 18%);
}

/* 标题栏 */
.ai-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: #fff;
  flex-shrink: 0;
}
.ai-panel__title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
}
.ai-panel__logo {
  width: 26px;
  height: 26px;
  border-radius: 50%;
}
.ai-panel__actions {
  display: flex;
  gap: 4px;
}
.ai-icon-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  color: rgb(255 255 255 / 85%);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition:
    background 0.2s,
    color 0.2s;
}
.ai-icon-btn:hover {
  background: rgb(255 255 255 / 18%);
  color: #fff;
}
.ai-icon-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 消息区 */
.ai-panel__body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: var(--el-fill-color-light, #f7f8fa);
}
.ai-panel__body::-webkit-scrollbar {
  width: 5px;
}
.ai-panel__body::-webkit-scrollbar-thumb {
  background: var(--el-border-color, #c0c4cc);
  border-radius: 3px;
}

/* 空状态 */
.ai-empty {
  text-align: center;
  padding: 16px 8px 8px;
}
.ai-empty__avatar {
  width: 56px;
  height: 56px;
  margin: 0 auto 14px;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  padding: 4px;
}
.ai-empty__avatar img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}
.ai-empty__welcome {
  color: var(--el-text-color-regular, #606266);
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 18px;
}
.ai-empty__chips {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: stretch;
}
.ai-chip {
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 10px;
  padding: 10px 14px;
  font-size: 13px;
  cursor: pointer;
  color: var(--el-text-color-primary, #303133);
  text-align: left;
  transition:
    border-color 0.2s,
    background 0.2s,
    transform 0.15s;
}
.ai-chip:hover {
  border-color: #6366f1;
  background: #f5f3ff;
  transform: translateY(-1px);
}
.ai-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

/* 消息行 */
.ai-row {
  display: flex;
  gap: 8px;
}
.ai-row--user {
  justify-content: flex-end;
}
.ai-row--bot {
  justify-content: flex-start;
  align-items: flex-start;
}
.ai-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  flex-shrink: 0;
  object-fit: cover;
}

/* 气泡 */
.ai-bubble {
  max-width: 290px;
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.ai-bubble--user {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: #fff;
  border-bottom-right-radius: 4px;
  box-shadow: 0 2px 8px rgb(99 102 241 / 25%);
}
.ai-bubble--bot {
  background: var(--el-bg-color, #fff);
  color: var(--el-text-color-primary, #303133);
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 4px rgb(0 0 0 / 4%);
}
.ai-bubble.is-error {
  color: var(--el-color-danger, #f56c6c);
}
.ai-bubble__text {
  display: block;
}

/* 思考中指示器 */
.ai-thinking {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary, #909399);
  font-size: 13px;
}
.ai-thinking__dots {
  display: inline-flex;
  gap: 3px;
}
.ai-thinking__dots i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #8b5cf6;
  display: inline-block;
  animation: ai-dot-bounce 1.3s infinite ease-in-out;
}
.ai-thinking__dots i:nth-child(2) {
  animation-delay: 0.18s;
}
.ai-thinking__dots i:nth-child(3) {
  animation-delay: 0.36s;
}
@keyframes ai-dot-bounce {
  0%,
  80%,
  100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}
.ai-thinking__label {
  font-weight: 500;
}
.ai-thinking__elapsed {
  color: var(--el-text-color-placeholder, #c0c4cc);
  font-size: 12px;
}

/* 流式光标 */
.ai-cursor {
  display: inline-block;
  width: 6px;
  height: 16px;
  background: #8b5cf6;
  margin-left: 2px;
  animation: ai-blink 1s step-end infinite;
  vertical-align: text-bottom;
  border-radius: 1px;
}
@keyframes ai-blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}

/* 工具确认卡片 */
.ai-card {
  margin-top: 10px;
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 10px;
  overflow: hidden;
  background: var(--el-fill-color-light, #f7f8fa);
}
.ai-card__title {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  background: var(--el-bg-color, #fff);
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
}
.ai-card__title-icon {
  font-size: 14px;
}
.ai-card__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.ai-card__label {
  padding: 5px 12px;
  color: var(--el-text-color-secondary, #909399);
  white-space: nowrap;
  width: 80px;
  font-weight: 500;
}
.ai-card__value {
  padding: 5px 12px;
  color: var(--el-text-color-primary, #303133);
  word-break: break-all;
}
.ai-card__actions {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
}
.ai-card__btn {
  flex: 1;
  border: 1px solid var(--el-border-color, #dcdfe6);
  background: #fff;
  border-radius: 8px;
  padding: 7px 0;
  font-size: 13px;
  cursor: pointer;
  color: var(--el-text-color-regular, #606266);
  transition:
    background 0.2s,
    border-color 0.2s;
}
.ai-card__btn:hover {
  background: var(--el-fill-color, #f5f7fa);
}
.ai-card__btn--primary {
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: #fff;
  border-color: transparent;
}
.ai-card__btn--primary:hover {
  opacity: 0.9;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
}
.ai-card__status {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary, #909399);
}
.ai-card__status--ok {
  color: var(--el-color-success, #67c23a);
}
.ai-card__status--err {
  color: var(--el-color-danger, #f56c6c);
}
.ai-spinner {
  width: 13px;
  height: 13px;
  border: 2px solid var(--el-border-color, #dcdfe6);
  border-top-color: #8b5cf6;
  border-radius: 50%;
  display: inline-block;
  animation: ai-spin 0.7s linear infinite;
}
@keyframes ai-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 输入区 */
.ai-panel__footer {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid var(--el-border-color-light, #e4e7ed);
  flex-shrink: 0;
  align-items: flex-end;
  background: var(--el-bg-color, #fff);
}
.ai-input {
  flex: 1;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 14px;
  resize: none;
  outline: none;
  font-family: inherit;
  background: var(--el-fill-color-light, #f7f8fa);
  color: var(--el-text-color-primary, #303133);
  transition:
    border-color 0.2s,
    background 0.2s;
  max-height: 100px;
}
.ai-input:focus {
  border-color: #6366f1;
  background: #fff;
}
.ai-input:disabled {
  opacity: 0.6;
}
.ai-panel__send {
  flex-shrink: 0;
}
.ai-send {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: #fff;
  transition:
    opacity 0.2s,
    transform 0.15s;
}
.ai-send:hover:not(:disabled) {
  opacity: 0.9;
  transform: translateY(-1px);
}
.ai-send:disabled {
  opacity: 0.4;
  cursor: not-allowed;
  background: var(--el-border-color, #dcdfe6);
}
.ai-send--stop {
  background: var(--el-color-danger, #f56c6c);
}

/* 动画 */
.ai-slide-enter-active,
.ai-slide-leave-active {
  transition:
    transform 0.25s ease,
    opacity 0.25s ease;
}
.ai-slide-enter-from,
.ai-slide-leave-to {
  transform: translateX(20px) scale(0.95);
  opacity: 0;
}
</style>
