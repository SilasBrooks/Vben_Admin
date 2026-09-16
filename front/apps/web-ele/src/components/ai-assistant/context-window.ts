/**
 * 会话上下文窗口构建（纯函数，独立于 Vue 依赖，可单独测试）。
 *
 * 规则（见 openspec/specs/ai/assistant「上下文记忆与持久化」）：
 * - 窗口最多保留 max 条协议消息，超窗时从切断点向后推进到最近的 user 消息
 *   （轮次起点），保证不会送出孤立的 tool / assistant(tool_calls) 消息导致上游 400
 * - 被移出窗口且尚未被摘要覆盖的消息作为「待摘要载荷」返回，由调用方请求
 *   后端合并为滚动摘要
 */

/** 窗口构建所需的最小消息结构（use-ai-chat 的 AiChatMessage 结构兼容） */
export interface WindowMessage {
  content?: null | string;
  role: 'assistant' | 'tool' | 'user';
  toolCallId?: string;
  toolCalls?: Array<{ arguments: string; id: string; name: string }>;
}

export interface WindowResult {
  /** 摘要覆盖进度：list 前 covered 条已被摘要或仍在窗口内 */
  covered: number;
  /** 尚未被摘要覆盖、且本次将被移出窗口的消息（需送后端合并摘要） */
  dropped: WindowMessage[];
  /** 实际随请求发送的消息（不含摘要消息本身） */
  window: WindowMessage[];
}

/** 窗口上限（协议消息条数） */
export const MAX_WINDOW_MESSAGES = 50;

export function buildContextWindow(
  list: WindowMessage[],
  covered: number,
  max: number = MAX_WINDOW_MESSAGES,
): WindowResult {
  // covered 越界防御（清空/恢复异常等场景回退到合法范围）
  const safeCovered = Math.min(Math.max(Math.trunc(covered) || 0, 0), list.length);
  let start = safeCovered;

  if (list.length - start > max) {
    // 切断点向后推进到完整轮次起点（user 消息）；跳过的碎片进入摘要载荷
    let cut = list.length - max;
    while (cut < list.length && list[cut]!.role !== 'user') {
      cut++;
    }
    start = cut;
  }

  const dropped = list.slice(safeCovered, start);
  return { covered: start, dropped, window: list.slice(start) };
}
