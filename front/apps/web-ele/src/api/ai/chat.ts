import { useAppConfig } from '@vben/hooks';
import { useAccessStore } from '@vben/stores';

import { requestClient } from '#/api/request';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);

/** 线上传输的消息形态（OpenAI 协议子集，与后端 DeepMessage 对齐） */
export interface AiWireMessage {
  content?: null | string;
  role: 'assistant' | 'system' | 'tool' | 'user';
  tool_call_id?: string;
  tool_calls?: Array<{
    function: { arguments: string; name: string };
    id: string;
    type: 'function';
  }>;
}

/** SSE toolcall 事件载荷（待确认的写操作） */
export interface AiToolCallPayload {
  args: Record<string, any>;
  title: string;
  toolCallId: string;
  toolName: string;
}

export interface StreamHandlers {
  /** 正文片段 */
  onDelta: (text: string) => void;
  /** 需要持久化的历史消息（按顺序） */
  onHistory: (messages: AiWireMessage[]) => void;
  /** 待确认的写操作 */
  onToolCall: (payload: AiToolCallPayload) => void;
  /** 本轮结束 */
  onDone: () => void;
  /** 可展示的错误 */
  onError: (message: string) => void;
}

export interface ExecuteToolParams {
  args: Record<string, any>;
  toolCallId: string;
  toolName: string;
}

export interface ExecuteToolResult {
  ok: boolean;
  summary: string;
}

/**
 * 流式对话：fetch + ReadableStream 读取自定义 SSE 事件。
 * 不使用 axios（浏览器侧 axios 不适合流式消费），支持 AbortController 中断。
 */
export async function streamAiChat(
  messages: AiWireMessage[],
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const accessStore = useAccessStore();
  let response: Response;
  try {
    response = await fetch(`${apiURL}/ai/chat`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessStore.accessToken ?? ''}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ messages }),
      signal,
    });
  } catch (error) {
    if (signal?.aborted) {
      handlers.onDone();
      return;
    }
    handlers.onError('网络异常，无法连接 AI 服务');
    return;
  }

  if (!response.ok || !response.body) {
    const message = await readErrorMessage(response);
    handlers.onError(message);
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      // SSE 事件以空行分隔
      let separatorIndex: number;
      while ((separatorIndex = buffer.indexOf('\n\n')) !== -1) {
        const rawEvent = buffer.slice(0, separatorIndex);
        buffer = buffer.slice(separatorIndex + 2);
        dispatchEvent(rawEvent, handlers);
      }
    }
    // 刷新缓冲区尾部
    if (buffer.trim()) {
      dispatchEvent(buffer, handlers);
    }
  } catch {
    if (!signal?.aborted) {
      handlers.onError('读取 AI 响应流中断，请稍后重试');
    }
  }
}

/** 用户确认后执行写操作 */
export async function executeAiToolApi(
  params: ExecuteToolParams,
): Promise<ExecuteToolResult> {
  return requestClient.post<ExecuteToolResult>('/ai/tool/execute', params);
}

export interface SummarizeParams {
  /** 被窗口移出的历史消息 */
  messages: AiWireMessage[];
  /** 已有的上一份摘要（可空） */
  priorSummary?: string;
}

export interface SummarizeResult {
  summary: string;
}

/** 会话滚动摘要：把被窗口移出的历史与已有摘要合并为新摘要（同步，耗时数秒） */
export async function summarizeAiChatApi(
  params: SummarizeParams,
): Promise<SummarizeResult> {
  return requestClient.post<SummarizeResult>('/ai/chat/summarize', params, {
    timeout: 30_000,
  });
}

// ------------------------------------------------------------------

/** 解析单个 SSE 事件块（event: 名称 + data: JSON） */
function dispatchEvent(rawEvent: string, handlers: StreamHandlers) {
  let eventName = '';
  const dataLines: string[] = [];
  for (const line of rawEvent.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim());
    }
  }
  if (!eventName || dataLines.length === 0) {
    return;
  }
  let payload: any = {};
  try {
    payload = JSON.parse(dataLines.join(''));
  } catch {
    return;
  }
  switch (eventName) {
    case 'delta': {
      handlers.onDelta(payload.text ?? '');
      break;
    }
    case 'history': {
      handlers.onHistory(Array.isArray(payload.messages) ? payload.messages : []);
      break;
    }
    case 'toolcall': {
      handlers.onToolCall(payload as AiToolCallPayload);
      break;
    }
    case 'done': {
      handlers.onDone();
      break;
    }
    case 'error': {
      handlers.onError(payload.message ?? 'AI 服务异常');
      break;
    }
  }
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const body = await response.text();
    const parsed = JSON.parse(body);
    if (response.status === 401) {
      return '登录已过期，请重新登录';
    }
    return parsed.message || parsed.error || `AI 服务返回错误（HTTP ${response.status}）`;
  } catch {
    return `AI 服务返回错误（HTTP ${response.status}）`;
  }
}
