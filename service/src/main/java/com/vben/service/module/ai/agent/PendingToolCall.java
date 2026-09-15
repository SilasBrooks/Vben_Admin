package com.vben.service.module.ai.agent;

/**
 * 等待用户确认的写操作（对应 SSE toolcall 事件）。
 *
 * @param toolCallId 模型返回的工具调用 id，确认/取消回传时对应
 * @param toolName   工具名
 * @param title      确认卡片中文标题
 * @param argsJson   原始参数 JSON（卡片渲染与确认执行都使用它）
 */
public record PendingToolCall(String toolCallId, String toolName, String title, String argsJson) {
}
