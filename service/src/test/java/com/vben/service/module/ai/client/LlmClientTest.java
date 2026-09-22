package com.vben.service.module.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;

/** LlmClient 请求地址拼接：兼容裸域名（OpenAI/DeepSeek）与带版本路径的厂商根地址 */
class LlmClientTest {

  @Test
  void bareDomainGetsV1Prefix() {
    assertEquals(URI.create("https://api.deepseek.com/v1/chat/completions"),
        LlmClient.chatCompletionsUri("https://api.deepseek.com"));
    assertEquals(URI.create("https://api.openai.com/v1/chat/completions"),
        LlmClient.chatCompletionsUri("https://api.openai.com"));
  }

  @Test
  void versionedPathAppendsActionOnly() {
    // 火山方舟 /api/v3
    assertEquals(URI.create("https://ark.cn-beijing.volces.com/api/v3/chat/completions"),
        LlmClient.chatCompletionsUri("https://ark.cn-beijing.volces.com/api/v3"));
    // GLM 多级版本路径
    assertEquals(URI.create("https://open.bigmodel.cn/api/paas/v4/chat/completions"),
        LlmClient.chatCompletionsUri("https://open.bigmodel.cn/api/paas/v4"));
    // dashscope 兼容模式
    assertEquals(URI.create("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"),
        LlmClient.chatCompletionsUri("https://dashscope.aliyuncs.com/compatible-mode/v1"));
  }

  @Test
  void trailingSlashIsStripped() {
    assertEquals(URI.create("https://api.deepseek.com/v1/chat/completions"),
        LlmClient.chatCompletionsUri("https://api.deepseek.com/"));
    assertEquals(URI.create("https://ark.cn-beijing.volces.com/api/v3/chat/completions"),
        LlmClient.chatCompletionsUri("https://ark.cn-beijing.volces.com/api/v3/"));
  }

  @Test
  void fullPathIsUsedAsIs() {
    assertEquals(URI.create("https://example.com/api/v1/chat/completions"),
        LlmClient.chatCompletionsUri("https://example.com/api/v1/chat/completions"));
  }
}
