package com.vben.service.module.ai.agent;

/**
 * 知识库引用溯源条目：search_knowledge 命中后随 citations SSE 事件下发，
 * 字段与回喂模型的 snippets 协议同源一致（content 已按工具截断）。
 *
 * @param docId 文档 id（前端「查看原文」跳转预览与去重 key 依据；0=存量消息无此字段）
 */
public record KbCitation(String docName, long docId, int seq, double score, String content) {
}
