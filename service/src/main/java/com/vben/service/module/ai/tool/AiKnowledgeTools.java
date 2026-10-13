package com.vben.service.module.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.module.ai.kb.KbSearchHit;
import com.vben.service.module.ai.kb.KbSearchService;
import com.vben.service.module.ai.tool.annotation.AiAgentTool;
import com.vben.service.module.ai.tool.annotation.AiToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识库 AI 工具：search_knowledge 查询类工具。
 *
 * <p>无权限码（登录即可调用，知识库面向全体登录用户）；自动执行回喂，
 * 未命中/失败返回状态说明而非异常，模型可如实转述、对话不中断。
 */
@Component
@RequiredArgsConstructor
public class AiKnowledgeTools {

  /** 工具名常量（AiChatService 据此识别命中后下发 citations 事件） */
  public static final String TOOL_NAME = "search_knowledge";

  /** 召回条数 */
  private static final int TOP_K = 4;

  /** 单块正文回喂截断（字符） */
  private static final int CHUNK_SNIPPET_CHARS = 500;

  /** 总回喂字符上限（防上下文爆炸） */
  private static final int TOTAL_CHARS_LIMIT = 3000;

  private final KbSearchService searchService;
  private final ObjectMapper objectMapper;

  @AiAgentTool(name = "search_knowledge", title = "检索知识库", kind = AiToolKind.QUERY,
      description = "在系统知识库中检索与问题相关的文档片段（向量语义检索）。"
          + "当用户询问业务操作、接入流程、制度规范、项目文档等内容，而其他系统工具无法回答时调用本工具。"
          + "返回相关片段（含文档名、段落序号、正文），回答时必须注明依据的文档名；"
          + "结果为空或失败时如实告知用户，绝不编造文档内容。")
  public AiToolResult searchKnowledge(
      @AiToolParam(value = "用户问题或检索关键词", required = true) String query) {
    List<KbSearchHit> hits;
    try {
      hits = searchService.search(query, TOP_K);
    } catch (Exception e) {
      // 检索失败不抛异常：返回状态说明，模型如实转述，本轮对话继续
      return AiToolResult.success(json(Map.of(
          "found", false,
          "reason", "知识库检索暂不可用，请告知用户稍后再试")));
    }
    if (hits.isEmpty()) {
      return AiToolResult.success(json(Map.of(
          "found", false,
          "reason", "知识库中未找到相关内容，请如实告知用户，不要编造")));
    }

    List<Map<String, Object>> snippets = new ArrayList<>();
    int total = 0;
    for (KbSearchHit hit : hits) {
      String content = hit.getContent() == null ? "" : hit.getContent();
      if (content.length() > CHUNK_SNIPPET_CHARS) {
        content = content.substring(0, CHUNK_SNIPPET_CHARS) + "…";
      }
      int added = content.length() + 60;
      if (total + added > TOTAL_CHARS_LIMIT) {
        break;
      }
      total += added;
      snippets.add(Map.of(
          "docName", hit.getFileName() == null ? "" : hit.getFileName(),
          "docId", hit.getDocId() == null ? 0L : hit.getDocId(),
          "seq", hit.getSeq() == null ? 0 : hit.getSeq(),
          "score", hit.getScore() == null ? 0.0 : Math.round(hit.getScore() * 1000) / 1000.0,
          "content", content));
    }
    return AiToolResult.success(json(Map.of("found", true, "snippets", snippets)));
  }

  private String json(Object obj) {
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (Exception e) {
      throw new IllegalStateException("工具结果序列化失败", e);
    }
  }
}
