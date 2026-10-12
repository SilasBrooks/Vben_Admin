package com.vben.service.module.ai.kb;

import org.springframework.stereotype.Component;

import java.util.List;

/** 文本提取器路由：按扩展名在全部实现中选择（无匹配即编程错误，上传白名单已拦截） */
@Component
public class KbExtractorRouter {

  private final List<DocumentTextExtractor> extractors;

  public KbExtractorRouter(List<DocumentTextExtractor> extractors) {
    this.extractors = extractors;
  }

  public DocumentTextExtractor route(String ext) {
    return extractors.stream()
        .filter(e -> e.supports(ext))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("不支持的文档格式: " + ext));
  }
}
