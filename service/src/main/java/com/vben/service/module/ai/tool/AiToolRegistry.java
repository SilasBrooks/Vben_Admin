package com.vben.service.module.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vben.service.common.BizException;
import com.vben.service.module.ai.tool.annotation.AiAgentTool;
import com.vben.service.security.LoginUser;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Component;

/**
 * AI 工具注册中心：启动期扫描所有 Spring Bean 上的 {@link AiAgentTool} 方法并注册为
 * {@link ToolHandler}。工具元数据与分发全部来自注册表，新增工具无需改动本类。
 *
 * <p>扫描 {@code AopUtils.getTargetClass(bean)}（用户类）上的注解方法，但持有的调用目标是
 * 容器代理 Bean——反射 invoke 走虚方法分派进入 AOP 拦截链，切面照常生效。
 * ObjectMapper 懒解析以规避 BeanPostProcessor 的早期初始化顺序问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiToolRegistry implements BeanPostProcessor, ApplicationContextAware {

  private final ParameterNameDiscoverer parameterNameDiscoverer =
      new DefaultParameterNameDiscoverer();

  /** name → handler，启动扫描完成后只读 */
  private final Map<String, ToolHandler> handlers = new LinkedHashMap<>();

  private ApplicationContext applicationContext;
  private volatile ObjectMapper objectMapper;

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    Class<?> targetClass = AopUtils.getTargetClass(bean);
    for (Method method : targetClass.getDeclaredMethods()) {
      AiAgentTool ann = method.getAnnotation(AiAgentTool.class);
      if (ann == null || method.isSynthetic() || method.isBridge()) {
        continue;
      }
      register(bean, method, ann);
    }
    return bean;
  }

  private void register(Object bean, Method userClassMethod, AiAgentTool ann) {
    String name = ann.name();
    if (handlers.containsKey(name)) {
      throw new IllegalStateException("AI 工具名重复：" + name + "（"
          + userClassMethod.getDeclaringClass().getName() + "#" + userClassMethod.getName() + "）");
    }
    String[] names = parameterNameDiscoverer.getParameterNames(userClassMethod);
    if (names == null) {
      throw new IllegalStateException("AI 工具 " + name + " 参数名无法解析，请确认编译带参数信息");
    }
    ToolHandler handler = ToolHandler.of(bean, userClassMethod, ann, mapper(),
        List.of(names));
    handlers.put(name, handler);
    log.info("注册 AI 工具：{} [{}] -> {}#{}", name, ann.kind(),
        userClassMethod.getDeclaringClass().getSimpleName(), userClassMethod.getName());
  }

  private ObjectMapper mapper() {
    if (objectMapper == null) {
      synchronized (this) {
        if (objectMapper == null) {
          objectMapper = applicationContext.getBean(ObjectMapper.class);
        }
      }
    }
    return objectMapper;
  }

  /** 全量工具定义（启动完成后调用） */
  public List<AiToolDef> all() {
    List<AiToolDef> list = new ArrayList<>();
    for (ToolHandler h : handlers.values()) {
      list.add(h.def());
    }
    return list;
  }

  /** DeepSeek tools 数组：仅返回当前用户有权限调用的工具 */
  public List<Map<String, Object>> schemasFor(LoginUser user) {
    List<Map<String, Object>> schemas = new ArrayList<>();
    for (ToolHandler h : handlers.values()) {
      if (canUse(user, h.def())) {
        schemas.add(h.def().toSchema());
      }
    }
    return schemas;
  }

  /** 当前用户是否可使用该工具（空权限码=仅登录即可） */
  public boolean canUse(LoginUser user, AiToolDef def) {
    if (def.permission() == null || def.permission().isBlank()) {
      return true;
    }
    return user != null && user.hasPermission(def.permission());
  }

  public ToolHandler require(String name) {
    ToolHandler handler = handlers.get(name);
    if (handler == null) {
      throw BizException.badRequest("error.ai.unknownTool", name);
    }
    return handler;
  }

  public AiToolDef requireDef(String name) {
    return require(name).def();
  }
}
