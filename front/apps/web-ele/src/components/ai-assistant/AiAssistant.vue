<script lang="ts" setup>
import { computed, nextTick, ref, watch } from 'vue';

import { $t } from '#/locales';

import { useAiChat } from './use-ai-chat';
import PlanCard from './PlanCard.vue';
import { formatCardArgs } from './tool-labels';

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
            class="ai-panel__btn"
            :title="$t('ai.chat.clearSession')"
            :disabled="loading"
            @click="clear"
          >
            {{ $t('ai.chat.clear') }}
          </button>
          <button class="ai-panel__btn" :title="$t('ai.chat.close')" @click="toggle">
            {{ $t('ai.chat.close') }}
          </button>
        </div>
      </header>

      <!-- 消息区 -->
      <div ref="scrollRef" class="ai-panel__body">
        <!-- 空状态 -->
        <div v-if="messages.length === 0" class="ai-empty">
          <p>{{ $t('ai.chat.welcome') }}</p>
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
              <span v-if="msg.content" class="ai-bubble__text">{{ msg.content }}</span>

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
                <div class="ai-card__title">📋 {{ card.title }}</div>
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
                  {{ $t('ai.card.executing') }}
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
              <span v-if="msg.streaming" class="ai-cursor" />
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
          <button v-if="loading" class="ai-send ai-send--stop" @click="stop">
            {{ $t('ai.chat.stop') }}
          </button>
          <button v-else class="ai-send" :disabled="!inputText.trim()" @click="handleSend">
            {{ $t('ai.chat.send') }}
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
  background: var(--el-color-primary, #409eff);
  box-shadow: 0 4px 16px rgb(0 0 0 / 20%);
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}
.ai-fab:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 20px rgb(0 0 0 / 30%);
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
  width: 380px;
  max-height: min(600px, 80vh);
  display: flex;
  flex-direction: column;
  z-index: 9999;
  border-radius: 12px;
  overflow: hidden;
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  box-shadow: 0 8px 32px rgb(0 0 0 / 15%);
}
.ai-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--el-color-primary, #409eff);
  color: #fff;
  flex-shrink: 0;
}
.ai-panel__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
}
.ai-panel__logo {
  width: 24px;
  height: 24px;
  border-radius: 50%;
}
.ai-panel__actions {
  display: flex;
  gap: 8px;
}
.ai-panel__btn {
  background: rgb(255 255 255 / 15%);
  color: #fff;
  border: none;
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s;
}
.ai-panel__btn:hover {
  background: rgb(255 255 255 / 25%);
}
.ai-panel__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ai-panel__body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.ai-panel__body::-webkit-scrollbar {
  width: 4px;
}
.ai-panel__body::-webkit-scrollbar-thumb {
  background: var(--el-border-color, #c0c4cc);
  border-radius: 2px;
}

.ai-empty {
  text-align: center;
  padding: 24px 8px;
  color: var(--el-text-color-secondary, #909399);
  font-size: 13px;
}
.ai-empty__chips {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 16px;
}
.ai-chip {
  background: var(--el-fill-color-light, #f5f7fa);
  border: 1px solid var(--el-border-color-light, #e4e7ed);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  cursor: pointer;
  color: var(--el-text-color-primary, #303133);
  transition:
    border-color 0.2s,
    background 0.2s;
}
.ai-chip:hover {
  border-color: var(--el-color-primary, #409eff);
  background: var(--el-color-primary-light-9, #ecf5ff);
}
.ai-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

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
  width: 28px;
  height: 28px;
  border-radius: 50%;
  flex-shrink: 0;
  object-fit: cover;
}
.ai-bubble {
  max-width: 280px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.ai-bubble--user {
  background: var(--el-color-primary, #409eff);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.ai-bubble--bot {
  background: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-text-color-primary, #303133);
  border-bottom-left-radius: 4px;
}
.ai-bubble.is-error {
  color: var(--el-color-danger, #f56c6c);
}
.ai-bubble__text {
  display: block;
}
.ai-cursor {
  display: inline-block;
  width: 6px;
  height: 14px;
  background: var(--el-color-primary, #409eff);
  margin-left: 2px;
  animation: ai-blink 1s step-end infinite;
  vertical-align: text-bottom;
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

.ai-card {
  margin-top: 8px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 8px;
  overflow: hidden;
  background: var(--el-bg-color, #fff);
}
.ai-card__title {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  background: var(--el-fill-color, #fafafa);
}
.ai-card__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.ai-card__label {
  padding: 4px 12px;
  color: var(--el-text-color-secondary, #909399);
  white-space: nowrap;
  width: 80px;
}
.ai-card__value {
  padding: 4px 12px;
  color: var(--el-text-color-primary, #303133);
}
.ai-card__actions {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
}
.ai-card__btn {
  flex: 1;
  border: 1px solid var(--el-border-color, #dcdfe6);
  background: #fff;
  border-radius: 6px;
  padding: 6px 0;
  font-size: 13px;
  cursor: pointer;
  color: var(--el-text-color-regular, #606266);
  transition: background 0.2s;
}
.ai-card__btn:hover {
  background: var(--el-fill-color, #f5f7fa);
}
.ai-card__btn--primary {
  background: var(--el-color-primary, #409eff);
  color: #fff;
  border-color: var(--el-color-primary, #409eff);
}
.ai-card__btn--primary:hover {
  background: var(--el-color-primary-light-3, #79bbff);
}
.ai-card__status {
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

.ai-panel__footer {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--el-border-color-light, #e4e7ed);
  flex-shrink: 0;
  align-items: flex-end;
}
.ai-input {
  flex: 1;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 14px;
  resize: none;
  outline: none;
  font-family: inherit;
  background: var(--el-bg-color, #fff);
  color: var(--el-text-color-primary, #303133);
  transition: border-color 0.2s;
  max-height: 100px;
}
.ai-input:focus {
  border-color: var(--el-color-primary, #409eff);
}
.ai-input:disabled {
  opacity: 0.6;
}
.ai-panel__send {
  flex-shrink: 0;
}
.ai-send {
  border: none;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 14px;
  cursor: pointer;
  background: var(--el-color-primary, #409eff);
  color: #fff;
  transition: opacity 0.2s;
}
.ai-send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.ai-send--stop {
  background: var(--el-color-danger, #f56c6c);
}

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
