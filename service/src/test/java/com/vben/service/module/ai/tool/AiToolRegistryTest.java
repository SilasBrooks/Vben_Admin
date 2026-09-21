package com.vben.service.module.ai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.BizException;
import com.vben.service.module.ai.tool.annotation.AiAgentTool;
import com.vben.service.module.ai.tool.annotation.AiToolParam;
import com.vben.service.security.LoginUser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;

/**
 * AI 工具注册中心与反射处理器单测：不启动 Spring 容器，
 * 直接让 {@link AiToolRegistry} 扫描内嵌测试 Bean。
 */
class AiToolRegistryTest {

  /** 供扫描的测试工具 Bean */
  static class TestTools {

    String capturedName;
    List<String> capturedTags;

    @AiAgentTool(name = "echo_int", title = "整数回显", kind = AiToolKind.QUERY,
        permission = "Test:Echo", description = "回显参数")
    public AiToolResult echoInt(
        @AiToolParam(value = "数字", required = true) Integer number,
        @AiToolParam("名字") String name,
        @AiToolParam("标签") List<String> tags) {
      this.capturedName = name;
      this.capturedTags = tags;
      return AiToolResult.success("{\"number\":" + number + "}");
    }

    @AiAgentTool(name = "danger_op", title = "危险操作", kind = AiToolKind.WRITE,
        permission = "Test:Danger", danger = true, description = "高危测试工具")
    public AiToolResult dangerOp() {
      return AiToolResult.created("{}", "完成");
    }

    @AiAgentTool(name = "login_only", title = "仅登录", kind = AiToolKind.QUERY,
        description = "空权限码工具")
    public AiToolResult loginOnly() {
      return AiToolResult.success("{}");
    }
  }

  private AiToolRegistry scan(TestTools bean) {
    ObjectMapper mapper = new ObjectMapper();
    AiToolRegistry registry = new AiToolRegistry();
    // ObjectMapper 懒加载：mock 一个只提供 ObjectMapper 的 ApplicationContext
    ApplicationContext ctx = org.mockito.Mockito.mock(ApplicationContext.class);
    org.mockito.Mockito.when(ctx.getBean(ObjectMapper.class)).thenReturn(mapper);
    registry.setApplicationContext(ctx);
    registry.postProcessAfterInitialization(bean, "testTools");
    return registry;
  }

  @Test
  void shouldScanToolsAndGenerateSchemaFromSignature() {
    AiToolRegistry registry = scan(new TestTools());

    AiToolDef def = registry.requireDef("echo_int");
    assertThat(def.title()).isEqualTo("整数回显");
    assertThat(def.kind()).isEqualTo(AiToolKind.QUERY);
    assertThat(def.permission()).isEqualTo("Test:Echo");
    assertThat(def.danger()).isFalse();

    @SuppressWarnings("unchecked")
    Map<String, Object> params = (Map<String, Object>) def.parameters();
    assertThat(params.get("type")).isEqualTo("object");
    assertThat((List<String>) params.get("required")).containsExactly("number");

    @SuppressWarnings("unchecked")
    Map<String, Object> props = (Map<String, Object>) params.get("properties");
    @SuppressWarnings("unchecked")
    Map<String, Object> numberSchema = (Map<String, Object>) props.get("number");
    assertThat(numberSchema).containsEntry("type", "integer");
    @SuppressWarnings("unchecked")
    Map<String, Object> nameSchema = (Map<String, Object>) props.get("name");
    assertThat(nameSchema).containsEntry("type", "string");
    @SuppressWarnings("unchecked")
    Map<String, Object> tagsSchema = (Map<String, Object>) props.get("tags");
    assertThat(tagsSchema).containsEntry("type", "array");
    @SuppressWarnings("unchecked")
    Map<String, Object> tagItems = (Map<String, Object>) tagsSchema.get("items");
    assertThat(tagItems).containsEntry("type", "string");
  }

  @Test
  void shouldInvokeMethodWithBoundArguments() throws Exception {
    TestTools bean = new TestTools();
    AiToolRegistry registry = scan(bean);

    // 通过 ToolHandler 直接验证参数名解析（-parameters / 调试符号表）
    ObjectMapper mapper = new ObjectMapper();
    DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
    java.lang.reflect.Method method = TestTools.class.getMethod("echoInt",
        Integer.class, String.class, List.class);
    String[] names = discoverer.getParameterNames(method);
    assertThat(names).isNotNull();
    assertThat(names).containsExactly("number", "name", "tags");

    JsonNode args = mapper.readTree(
        "{\"number\":7,\"name\":\"测试人\",\"tags\":[\"a\",\"b\"]}");
    AiToolResult result = (AiToolResult) registry.require("echo_int").invoke(args);

    assertThat(result.ok()).isTrue();
    assertThat(result.content()).contains("\"number\":7");
    assertThat(bean.capturedName).isEqualTo("测试人");
    assertThat(bean.capturedTags).containsExactly("a", "b");
  }

  @Test
  void missingOptionalArgumentsBindToNull() throws Exception {
    TestTools bean = new TestTools();
    ObjectMapper mapper = new ObjectMapper();
    AiToolRegistry registry = scan(bean);
    JsonNode args = mapper.readTree("{\"number\":1}");
    AiToolResult result = (AiToolResult) registry.require("echo_int").invoke(args);
    assertThat(result.ok()).isTrue();
    assertThat(bean.capturedName).isNull();
    assertThat(bean.capturedTags).isNull();
  }

  @Test
  void shouldFilterToolsByPermission() {
    AiToolRegistry registry = scan(new TestTools());

    LoginUser withPermission = new LoginUser(1L, "tester", List.of(),
        java.util.Set.of("Test:Echo"), 0L);
    LoginUser withoutPermission = new LoginUser(2L, "guest", List.of(),
        java.util.Set.of(), 0L);

    assertThat(registry.canUse(withPermission, registry.requireDef("echo_int"))).isTrue();
    assertThat(registry.canUse(withoutPermission, registry.requireDef("echo_int"))).isFalse();
    // 空权限码：登录即可
    assertThat(registry.canUse(withoutPermission, registry.requireDef("login_only"))).isTrue();

    // schemasFor 只包含有权限的工具（login_only 空权限码也包含）
    List<String> visible = registry.schemasFor(withPermission).stream()
        .map(s -> ((Map<?, ?>) ((Map<?, ?>) s.get("function"))).get("name").toString())
        .toList();
    assertThat(visible).contains("echo_int", "login_only").doesNotContain("danger_op");
  }

  @Test
  void unknownToolThrowsBizException() {
    AiToolRegistry registry = scan(new TestTools());
    assertThatThrownBy(() -> registry.require("not_exists"))
        .isInstanceOf(BizException.class);
  }
}
