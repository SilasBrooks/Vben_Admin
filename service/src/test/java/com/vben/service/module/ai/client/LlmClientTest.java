package com.vben.service.module.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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

  // ============ usage 分片解析（AI 用量采集） ============

  /** 收集 onUsage 回调并忽略文本/工具回调的最小 handler */
  private static class UsageRecordingHandler implements StreamHandler {
    final List<LlmUsage> usages = new ArrayList<>();
    final StringBuilder text = new StringBuilder();

    @Override
    public void onText(String delta) {
      text.append(delta);
    }

    @Override
    public void onToolCalls(List<ToolCall> toolCalls) {
    }

    @Override
    public void onUsage(LlmUsage usage) {
      usages.add(usage);
    }
  }

  @SuppressWarnings("unchecked")
  private HttpResponse<Stream<String>> sseResponse(String... lines) {
    HttpResponse<Stream<String>> response = Mockito.mock(HttpResponse.class);
    when(response.body()).thenReturn(Arrays.stream(lines));
    return response;
  }

  @Test
  void usageChunkFiresSingleCallbackWithTokens() {
    HttpResponse<Stream<String>> response = sseResponse(
        "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}",
        "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}",
        "data: [DONE]");
    UsageRecordingHandler handler = new UsageRecordingHandler();

    new LlmClient(null).parseSse(response, handler, new CancelToken(), "test-model",
        System.currentTimeMillis());

    // usage 分片（choices 为空数组）不再被提前跳过，且恰回调一次
    assertEquals(1, handler.usages.size());
    assertEquals("test-model", handler.usages.get(0).model());
    assertEquals(10L, handler.usages.get(0).promptTokens());
    assertEquals(5L, handler.usages.get(0).completionTokens());
    assertEquals(15L, handler.usages.get(0).totalTokens());
    // 内容分片解析不回归
    assertEquals("你好", handler.text.toString());
  }

  @Test
  void repeatedUsageChunksOnlyFireFirst() {
    // 个别网关每个分片都带 usage：只取首片，避免一次请求记多条
    HttpResponse<Stream<String>> response = sseResponse(
        "data: {\"choices\":[{\"delta\":{\"content\":\"a\"}}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}",
        "data: {\"choices\":[{\"delta\":{\"content\":\"b\"}}],\"usage\":{\"prompt_tokens\":9,\"completion_tokens\":9,\"total_tokens\":18}}",
        "data: [DONE]");
    UsageRecordingHandler handler = new UsageRecordingHandler();

    new LlmClient(null).parseSse(response, handler, new CancelToken(), "m",
        System.currentTimeMillis());

    assertEquals(1, handler.usages.size());
    assertEquals(2L, handler.usages.get(0).totalTokens());
  }

  @Test
  void streamWithoutUsageFiresNullTokensCallback() {
    // 上游未返回 usage：仍回调一次（tokens 为 null，调用次数保持准确）
    HttpResponse<Stream<String>> response = sseResponse(
        "data: {\"choices\":[{\"delta\":{\"content\":\"部分\"}}]}",
        "data: {\"choices\":[{\"delta\":{\"content\":\"回复\"}}]}",
        "data: [DONE]");
    UsageRecordingHandler handler = new UsageRecordingHandler();

    new LlmClient(null).parseSse(response, handler, new CancelToken(), "m",
        System.currentTimeMillis());

    assertEquals(1, handler.usages.size());
    assertNull(handler.usages.get(0).promptTokens());
    assertNull(handler.usages.get(0).completionTokens());
    assertNull(handler.usages.get(0).totalTokens());
  }
}
