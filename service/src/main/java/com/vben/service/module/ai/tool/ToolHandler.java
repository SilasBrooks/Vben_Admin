package com.vben.service.module.ai.tool;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.BizException;
import com.vben.service.module.ai.tool.annotation.AiAgentTool;
import com.vben.service.module.ai.tool.annotation.AiToolParam;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个被注册的 AI 工具：持有 Spring 代理 Bean 与方法，负责参数 JSON Schema 生成与反射调用。
 *
 * <p>反射调用目标是容器里的代理 Bean，业务方法上的 AOP 切面（@DataScope/@Idempotent/
 * @OperLog 等）正常生效。
 */
public class ToolHandler {

  private final Object bean;
  private final Method method;
  private final AiToolDef def;
  private final ObjectMapper objectMapper;
  private final String[] paramNames;
  private final JavaType[] paramJavaTypes;

  private ToolHandler(Object bean, Method method, AiToolDef def, ObjectMapper objectMapper,
      String[] paramNames, JavaType[] paramJavaTypes) {
    this.bean = bean;
    this.method = method;
    this.def = def;
    this.objectMapper = objectMapper;
    this.paramNames = paramNames;
    this.paramJavaTypes = paramJavaTypes;
  }

  public AiToolDef def() {
    return def;
  }

  public String name() {
    return def.name();
  }

  /**
   * 从带注解的方法构建处理器。
   *
   * @param names 由 ParameterNameDiscoverer 解析出的参数名（与形参一一对应）
   */
  public static ToolHandler of(Object bean, Method method, AiAgentTool ann, ObjectMapper mapper,
      List<String> names) {
    Parameter[] parameters = method.getParameters();
    if (parameters.length != names.size()) {
      throw new IllegalStateException("AI 工具 " + ann.name() + " 参数名解析失败：方法 "
          + method.getDeclaringClass().getSimpleName() + "#" + method.getName());
    }

    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    JavaType[] javaTypes = new JavaType[parameters.length];

    for (int i = 0; i < parameters.length; i++) {
      Parameter p = parameters[i];
      AiToolParam paramAnn = p.getAnnotation(AiToolParam.class);
      String desc = paramAnn == null ? "" : paramAnn.value();
      boolean isRequired = paramAnn != null && paramAnn.required();

      Type genericType = method.getGenericParameterTypes()[i];
      JavaType javaType = mapper.getTypeFactory().constructType(genericType);
      javaTypes[i] = javaType;

      Map<String, Object> schema = new LinkedHashMap<>(jsonSchemaOf(p.getType(), genericType));
      schema.put("description", desc);
      properties.put(names.get(i), schema);
      if (isRequired) {
        required.add(names.get(i));
      }
    }

    Map<String, Object> parametersSchema = new LinkedHashMap<>();
    parametersSchema.put("type", "object");
    parametersSchema.put("properties", properties);
    parametersSchema.put("required", required);

    AiToolDef def = new AiToolDef(ann.name(), ann.title(), ann.kind(), ann.permission(),
        ann.danger(), ann.description(), parametersSchema);
    return new ToolHandler(bean, method, def, mapper, names.toArray(String[]::new), javaTypes);
  }

  /**
   * 绑定参数并反射调用。
   *
   * @param args 模型产出的参数 JSON
   * @return 方法返回值（工具方法约定返回 {@link AiToolResult}；其他类型由执行器统一包装）
   */
  public Object invoke(JsonNode args) {
    Object[] values = new Object[paramNames.length];
    for (int i = 0; i < paramNames.length; i++) {
      JsonNode node = args == null ? null : args.get(paramNames[i]);
      Class<?> raw = paramJavaTypes[i].getRawClass();
      if (node == null || node.isNull()) {
        // 缺失/显式 null：引用类型传 null；基本类型给 JVM 默认值
        values[i] = defaultValue(raw);
      } else {
        try {
          values[i] = objectMapper.convertValue(node, paramJavaTypes[i]);
        } catch (IllegalArgumentException e) {
          throw BizException.badRequest("error.ai.paramType", paramNames[i]);
        }
      }
    }
    try {
      method.setAccessible(true);
      return method.invoke(bean, values);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("AI 工具调用失败：" + def.name(), e);
    } catch (InvocationTargetException e) {
      // 业务方法抛出的异常还原，保持与直接调用一致（BizException 走全局处理/回喂）
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException re) {
        throw re;
      }
      if (cause instanceof Error err) {
        throw err;
      }
      throw new IllegalStateException("AI 工具执行失败：" + def.name(), cause);
    }
  }

  // ------------------------------------------------------------------
  // Java 类型 → JSON Schema
  // ------------------------------------------------------------------

  private static Map<String, Object> jsonSchemaOf(Class<?> type, Type genericType) {
    if (type == String.class || type == Character.class || type == char.class) {
      return Map.of("type", "string");
    }
    if (type == Integer.class || type == int.class
        || type == Long.class || type == long.class
        || type == Short.class || type == short.class) {
      return Map.of("type", "integer");
    }
    if (type == Double.class || type == double.class
        || type == Float.class || type == float.class) {
      return Map.of("type", "number");
    }
    if (type == Boolean.class || type == boolean.class) {
      return Map.of("type", "boolean");
    }
    if (type.isEnum()) {
      Object[] constants = type.getEnumConstants();
      List<String> values = new ArrayList<>();
      for (Object c : constants) {
        values.add(((Enum<?>) c).name());
      }
      return Map.of("type", "string", "enum", values);
    }
    if (Collection.class.isAssignableFrom(type)) {
      Map<String, Object> itemSchema = Map.of("type", "string");
      if (genericType instanceof java.lang.reflect.ParameterizedType pt
          && pt.getActualTypeArguments()[0] instanceof Class<?> itemClass) {
        itemSchema = jsonSchemaOf(itemClass, itemClass);
      }
      return Map.of("type", "array", "items", itemSchema);
    }
    // 复杂对象/Map 等兜底为字符串，避免模型产出嵌套结构；工具参数保持扁平简单
    return Map.of("type", "string");
  }

  private static Object defaultValue(Class<?> raw) {
    if (!raw.isPrimitive()) {
      return null;
    }
    if (raw == boolean.class) {
      return false;
    }
    if (raw == char.class) {
      return '\0';
    }
    // 其余基本数值类型默认 0
    return 0;
  }
}
