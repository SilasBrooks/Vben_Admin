import { ref } from 'vue';

import { useUserStore } from '@vben/stores';

import {
  executeAiToolApi,
  streamAiChat,
  summarizeAiChatApi,
  type AiToolCallPayload,
  type AiWireMessage,
} from '#/api/ai/chat';

import {
  buildContextWindow,
  MAX_WINDOW_MESSAGES,
  type WindowMessage,
} from './context-window';

/** 确认卡片状态 */
export type ToolCardStatus =
  | 'cancelled'
  | 'done'
  | 'error'
  | 'executing'
  | 'pending';

export interface AiToolCardState extends AiToolCallPayload {
  errorMsg?: string;
  status: ToolCardStatus;
  summary?: string;
}

/** 前端会话消息（含 UI 状态） */
export interface AiChatMessage {
  /** 助手消息内的确认卡片 */
  cards?: AiToolCardState[];
  content?: null | string;
  id: number;
  isError?: boolean;
  role: 'assistant' | 'tool' | 'user';
  /** 正在流式输出中 */
  streaming?: boolean;
  /** role=tool 的关联 id（不渲染气泡，但要回传） */
  toolCallId?: string;
  /** 助手发起的工具调用（回传给后端） */
  toolCalls?: Array<{ arguments: string; id: string; name: string }>;
}

let messageIdSeed = 1;

function nextId() {
  return messageIdSeed++;
}

/** 本地持久化 key 前缀（按用户 id 隔离），v1 为结构版本 */
const STORAGE_PREFIX = 'vben-ai-chat-v1:';
/** 本地最多持久化的消息条数（超出裁掉最旧头部） */
const MAX_PERSISTED_MESSAGES = 500;

/**
 * AI 会话编排：消息状态、流式生命周期（单航道 AbortController）、
 * 写操作确认/取消后的二轮对话驱动、超窗滚动摘要（见 context-window.ts）、
 * 本地持久化（刷新后恢复，按用户隔离）。
 */
export function useAiChat() {
  const userStore = useUserStore();
  const storageKey = `${STORAGE_PREFIX}${userStore.userInfo?.userId ?? 'anon'}`;

  const messages = ref<AiChatMessage[]>([]);
  /** 滚动摘要：覆盖已移出窗口的历史轮次，随每次请求以 system 消息注入 */
  const summary = ref('');
  /** 摘要覆盖进度：messages 前 covered 条已被摘要或移出窗口 */
  let covered = 0;
  /** 是否正在生成摘要（防并发重复调用） */
  let summarizing = false;
  const loading = ref(false);
  let controller: AbortController | null = null;
  /** 当前流式气泡 id（delta 归属） */
  let activeId: null | number = null;

  restore();

  function stop() {
    controller?.abort();
    controller = null;
    loading.value = false;
    finalizeActive();
  }

  function clear() {
    controller?.abort();
    controller = null;
    loading.value = false;
    activeId = null;
    messages.value = [];
    summary.value = '';
    covered = 0;
    try {
      localStorage.removeItem(storageKey);
    } catch {
      // 存储不可用时忽略
    }
  }

  /** 发送一条用户消息，或不带参数地继续会话（确认/取消后的二轮调用） */
  async function send(text?: string) {
    // 单航道：新请求前中断上一条未完成的流
    controller?.abort();

    if (text && text.trim()) {
      messages.value.push({ id: nextId(), role: 'user', content: text.trim() });
    }

    // 超窗时先滚动摘要；失败降级为直接截断，不阻断当前提问
    const plan = buildContextWindow(
      messages.value,
      covered,
      MAX_WINDOW_MESSAGES,
    );
    if (plan.dropped.length > 0 && !summarizing) {
      summarizing = true;
      try {
        const { summary: merged } = await summarizeAiChatApi({
          messages: plan.dropped.map((m) => toWireMessage(m)),
          priorSummary: summary.value,
        });
        summary.value = merged;
        covered = plan.covered;
      } catch {
        // 摘要失败：旧轮次直接移出窗口（摘要不更新），对话照常继续
        covered = plan.covered;
      } finally {
        summarizing = false;
      }
    } else if (plan.dropped.length === 0) {
      covered = plan.covered;
    }
    // 摘要进行中（并发发送）：covered 保持不变，下轮重试摘要

    // 摘要作为 system 元上下文注入，仅存在于发送载荷，不进 UI 列表
    const wire: AiWireMessage[] = [];
    if (summary.value) {
      wire.push({
        content: `【此前对话摘要】\n${summary.value}`,
        role: 'system',
      });
    }
    wire.push(...plan.window.map((m) => toWireMessage(m)));

    controller = new AbortController();
    loading.value = true;
    activeId = null;

    await streamAiChat(
      wire,
      {
        onDelta: (delta) => {
          const bubble = ensureActiveBubble();
          bubble.content = (bubble.content ?? '') + delta;
        },
        onHistory: (entries) => reconcileHistory(entries),
        onToolCall: (payload) => attachCard(payload),
        onDone: () => finishRound(),
        onError: (message) => {
          const bubble = ensureActiveBubble();
          bubble.content = (bubble.content && bubble.content.trim())
            ? bubble.content
            : message;
          bubble.isError = true;
          finishRound();
        },
      },
      controller.signal,
    );
  }

  /** 确认执行写操作：落库 → 工具结果回喂 → 再发起一轮让模型汇报 */
  async function confirmCard(card: AiToolCardState) {
    card.status = 'executing';
    try {
      const result = await executeAiToolApi({
        args: card.args,
        toolCallId: card.toolCallId,
        toolName: card.toolName,
      });
      card.status = 'done';
      card.summary = result.summary;
      pushToolMessage(
        card.toolCallId,
        JSON.stringify({ ok: true, summary: result.summary }),
      );
      await send();
    } catch (error: any) {
      card.status = 'error';
      card.errorMsg = error?.message || '执行失败，请稍后重试';
    } finally {
      persist();
    }
  }

  /** 取消写操作：回喂取消结果，让模型简短确认 */
  async function cancelCard(card: AiToolCardState) {
    card.status = 'cancelled';
    pushToolMessage(
      card.toolCallId,
      '用户已取消该操作，未执行任何数据变更，请用一句话告知用户操作已取消',
    );
    await send();
  }

  // ----------------------------------------------------------------
  // 内部实现
  // ----------------------------------------------------------------

  function ensureActiveBubble(): AiChatMessage {
    if (activeId !== null) {
      const found = messages.value.find((m) => m.id === activeId);
      if (found) return found;
    }
    const bubble: AiChatMessage = {
      content: '',
      id: nextId(),
      role: 'assistant',
      streaming: true,
    };
    messages.value.push(bubble);
    activeId = bubble.id;
    return bubble;
  }

  function finalizeActive() {
    if (activeId !== null) {
      const found = messages.value.find((m) => m.id === activeId);
      if (found) found.streaming = false;
      activeId = null;
    }
  }

  function finishRound() {
    finalizeActive();
    loading.value = false;
    controller = null;
    persist();
  }

  /**
   * 对账后端 history 事件：
   * - assistant 条目复用当前流式气泡（可能无正文但带 tool_calls），无气泡则补建
   * - tool 条目作为静默消息保存（渲染时不显示，但下轮必须回传）
   */
  function reconcileHistory(entries: AiWireMessage[]) {
    for (const entry of entries) {
      if (entry.role === 'assistant') {
        let bubble: AiChatMessage | undefined;
        if (activeId !== null) {
          bubble = messages.value.find((m) => m.id === activeId);
        }
        if (bubble) {
          bubble.content = entry.content;
          bubble.streaming = false;
          bubble.toolCalls = entry.tool_calls?.map((tc) => ({
            arguments: tc.function.arguments,
            id: tc.id,
            name: tc.function.name,
          }));
          activeId = null;
        } else {
          messages.value.push({
            content: entry.content,
            id: nextId(),
            role: 'assistant',
            toolCalls: entry.tool_calls?.map((tc) => ({
              arguments: tc.function.arguments,
              id: tc.id,
              name: tc.function.name,
            })),
          });
        }
      } else if (entry.role === 'tool') {
        messages.value.push({
          content: entry.content,
          id: nextId(),
          role: 'tool',
          toolCallId: entry.tool_call_id,
        });
      }
    }
  }

  /** 将 toolcall 事件挂到含对应 toolCalls 的助手气泡上 */
  function attachCard(payload: AiToolCallPayload) {
    const target = [...messages.value]
      .reverse()
      .find(
        (m) =>
          m.role === 'assistant' &&
          m.toolCalls?.some((tc) => tc.id === payload.toolCallId),
      );
    if (!target) return;
    target.cards ??= [];
    if (!target.cards.some((c) => c.toolCallId === payload.toolCallId)) {
      target.cards.push({ ...payload, status: 'pending' });
    }
  }

  function pushToolMessage(toolCallId: string, content: string) {
    messages.value.push({
      content,
      id: nextId(),
      role: 'tool',
      toolCallId,
    });
  }

  /** 单条 UI 消息 → 线上协议消息（接受窗口模块的结构化类型） */
  function toWireMessage(m: WindowMessage): AiWireMessage {
    const wire: AiWireMessage = {
      content: m.content ?? null,
      role: m.role,
    };
    if (m.toolCalls?.length) {
      wire.tool_calls = m.toolCalls.map((tc) => ({
        function: { arguments: tc.arguments, name: tc.name },
        id: tc.id,
        type: 'function' as const,
      }));
    }
    if (m.toolCallId) {
      wire.tool_call_id = m.toolCallId;
    }
    return wire;
  }

  /** 刷新后恢复会话（消息、摘要、覆盖进度），id 种子续号 */
  function restore() {
    try {
      const raw = localStorage.getItem(storageKey);
      if (!raw) return;
      const data = JSON.parse(raw) as {
        covered?: number;
        messages?: AiChatMessage[];
        summary?: string;
        v?: number;
      };
      if (data?.v !== 1 || !Array.isArray(data.messages)) return;
      messages.value = data.messages;
      summary.value = typeof data.summary === 'string' ? data.summary : '';
      covered = Math.min(
        Math.max(Math.trunc(data.covered ?? 0) || 0, 0),
        messages.value.length,
      );
      const maxId = messages.value.reduce((m, x) => Math.max(m, x?.id ?? 0), 0);
      messageIdSeed = Math.max(messageIdSeed, maxId + 1);
    } catch {
      // 本地数据损坏时忽略，使用全新会话
    }
  }

  /** 持久化到本地存储（轮次结束/卡片终态时触发，不在流式 delta 中频繁写入） */
  function persist() {
    try {
      // 上限保护：裁掉最旧头部，同步平移摘要覆盖进度
      let list = messages.value;
      const trim = Math.max(0, list.length - MAX_PERSISTED_MESSAGES);
      if (trim > 0) list = list.slice(trim);
      localStorage.setItem(
        storageKey,
        JSON.stringify({
          covered: Math.max(0, covered - trim),
          messages: list,
          summary: summary.value,
          v: 1,
        }),
      );
    } catch {
      // 存储满/禁用等场景忽略
    }
  }

  return {
    cancelCard,
    clear,
    confirmCard,
    loading,
    messages,
    send,
    stop,
  };
}
