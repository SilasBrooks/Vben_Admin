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

/** 计划卡中的单个步骤（服务端下发，标题/高危标记以服务端为准） */
export interface AiPlanStep {
  args: Record<string, any> | string;
  danger: boolean;
  index: number;
  reason: string;
  title: string;
  tool: string;
}

/** chat SSE 的 plan 事件载荷 */
export interface AiPlanPayload {
  goal: string;
  steps: AiPlanStep[];
  toolCallId: string;
}

/** 计划执行结果条目（plan_done 事件） */
export interface AiPlanStepResult {
  index: number;
  ok: boolean;
  summary: string;
  title: string;
}

export interface PlanStreamHandlers {
  /** 计划开始（planId 用于高危步骤继续/取消） */
  onStarted: (
    planId: string,
    goal: string,
    steps: AiPlanStep[],
  ) => void;
  /** 某步开始 */
  onStepStart: (index: number, title: string) => void;
  /** 某步成功 */
  onStepDone: (index: number, title: string, summary: string) => void;
  /** 高危步骤等待二次确认 */
  onNeedConfirm: (index: number, title: string, reason: string) => void;
  /** 某步失败 */
  onStepFailed: (index: number, title: string, error: string) => void;
  /** 计划结束：completed / cancelled / failed */
  onPlanDone: (
    status: 'cancelled' | 'completed' | 'failed',
    results: AiPlanStepResult[],
  ) => void;
  onError: (message: string) => void;
}

export interface StreamHandlers {
  /** 正文片段 */
  onDelta: (text: string) => void;
  /** 需要持久化的历史消息（按顺序） */
  onHistory: (messages: AiWireMessage[]) => void;
  /** 待确认的写操作 */
  onToolCall: (payload: AiToolCallPayload) => void;
  /** 多步任务执行计划，待用户确认 */
  onPlan: (payload: AiPlanPayload) => void;
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
  await ssePost(
    '/ai/chat',
    { messages },
    (eventName: string, payload: any) => {
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
        case 'plan': {
          handlers.onPlan(payload as AiPlanPayload);
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
    },
    handlers,
    signal,
  );
}

/** 用户确认计划后顺序执行（SSE 步骤流） */
export async function executeAiPlanStream(
  goal: string,
  steps: Array<{ args: any; reason: string; tool: string }>,
  handlers: PlanStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  await ssePost('/ai/plan/execute', { goal, steps }, makePlanDispatcher(handlers), handlers, signal);
}

/** 高危步骤二次确认（confirmed=true）或取消（false）后继续（SSE） */
export async function continueAiPlanStream(
  planId: string,
  confirmed: boolean,
  handlers: PlanStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  await ssePost(
    '/ai/plan/continue',
    { confirmed, planId },
    makePlanDispatcher(handlers),
    handlers,
    signal,
  );
}

/** 计划 SSE 事件分发（execute / continue 复用） */
function makePlanDispatcher(handlers: PlanStreamHandlers) {
  return (eventName: string, payload: any) => {
    switch (eventName) {
      case 'plan_started': {
        handlers.onStarted(payload.planId, payload.goal ?? '', payload.steps ?? []);
        break;
      }
      case 'step_start': {
        handlers.onStepStart(payload.index, payload.title);
        break;
      }
      case 'step_done': {
        handlers.onStepDone(payload.index, payload.title, payload.summary ?? '');
        break;
      }
      case 'step_need_confirm': {
        handlers.onNeedConfirm(payload.index, payload.title, payload.reason ?? '');
        break;
      }
      case 'step_failed': {
        handlers.onStepFailed(payload.index, payload.title, payload.error ?? '执行失败');
        break;
      }
      case 'plan_done': {
        handlers.onPlanDone(payload.status, payload.results ?? []);
        break;
      }
      case 'plan_error':
      case 'error': {
        handlers.onError(payload.message ?? '计划执行异常');
        break;
      }
    }
  };
}

/**
 * AI 接口通用 SSE POST：Bearer 认证 + ReadableStream 按空行切事件。
 * @param dispatch 事件回调（event 名称 + data JSON）
 */
async function ssePost(
  path: string,
  body: unknown,
  dispatch: (eventName: string, payload: any) => void,
  errorHandlers: { onError: (message: string) => void },
  signal?: AbortSignal,
): Promise<void> {
  const accessStore = useAccessStore();
  let response: Response;
  try {
    response = await fetch(`${apiURL}${path}`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessStore.accessToken ?? ''}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      signal,
    });
  } catch {
    if (signal?.aborted) {
      errorHandlers.onError('');
      return;
    }
    errorHandlers.onError('网络异常，无法连接 AI 服务');
    return;
  }

  if (!response.ok || !response.body) {
    errorHandlers.onError(await readErrorMessage(response));
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

      let separatorIndex: number;
      while ((separatorIndex = buffer.indexOf('\n\n')) !== -1) {
        const rawEvent = buffer.slice(0, separatorIndex);
        buffer = buffer.slice(separatorIndex + 2);
        dispatchSseEvent(rawEvent, dispatch);
      }
    }
    if (buffer.trim()) {
      dispatchSseEvent(buffer, dispatch);
    }
  } catch {
    if (!signal?.aborted) {
      errorHandlers.onError('读取 AI 响应流中断，请稍后重试');
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

/** 解析单个 SSE 事件块（event: 名称 + data: JSON）后回调分发 */
function dispatchSseEvent(
  rawEvent: string,
  dispatch: (eventName: string, payload: any) => void,
) {
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
  dispatch(eventName, payload);
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
