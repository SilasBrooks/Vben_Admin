import { ref } from 'vue';

import {
  executeAiToolApi,
  streamAiChat,
  type AiToolCallPayload,
  type AiWireMessage,
} from '#/api/ai/chat';

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

/**
 * AI 会话编排：消息状态、流式生命周期（单航道 AbortController）、
 * 写操作确认/取消后的二轮对话驱动。
 */
export function useAiChat() {
  const messages = ref<AiChatMessage[]>([]);
  const loading = ref(false);
  let controller: AbortController | null = null;
  /** 当前流式气泡 id（delta 归属） */
  let activeId: null | number = null;

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
  }

  /** 发送一条用户消息，或不带参数地继续会话（确认/取消后的二轮调用） */
  async function send(text?: string) {
    // 单航道：新请求前中断上一条未完成的流
    controller?.abort();

    if (text && text.trim()) {
      messages.value.push({ id: nextId(), role: 'user', content: text.trim() });
    }

    controller = new AbortController();
    loading.value = true;
    activeId = null;

    await streamAiChat(
      toWireMessages(messages.value),
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

  /** UI 消息 → 线上协议消息；截断最近 20 条 */
  function toWireMessages(list: AiChatMessage[]): AiWireMessage[] {
    return list.slice(-20).map((m) => {
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
    });
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
