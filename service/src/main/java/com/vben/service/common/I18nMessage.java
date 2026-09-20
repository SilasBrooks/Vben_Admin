package com.vben.service.common;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 后端错误消息 i18n 工具：按当前请求语言解析 messages.properties 文案。
 *
 * <p>语言来源 = Accept-Language 请求头（前端 requestClient 每请求自动携带，
 * 随界面语言同步切换）；无 header（curl/E2E/服务间调用）回落 zh_CN。
 *
 * <p>静态持有 MessageSource（由 {@code I18nConfig} 装配），供 BizException 工厂等
 * 非 Spring 管理的调用点使用；MessageSource 未就绪（单测/无容器）时原样返回 key，
 * 保证「未迁移的文案」与「无容器场景」安全降级。
 */
public final class I18nMessage {

  private static volatile MessageSource messageSource;

  private I18nMessage() {
  }

  static void init(MessageSource source) {
    I18nMessage.messageSource = source;
  }

  /** 按当前请求语言解析文案；未命中 key 或容器未就绪时原样返回 code */
  public static String get(String code, Object... args) {
    MessageSource source = messageSource;
    if (source == null) {
      return code;
    }
    try {
      return source.getMessage(code, args, LocaleContextHolder.getLocale());
    } catch (NoSuchMessageException ex) {
      return code;
    }
  }

  /** 指定语言解析（Filter 等 LocaleContextHolder 未生效的场景用） */
  public static String get(Locale locale, String code, Object... args) {
    MessageSource source = messageSource;
    if (source == null) {
      return code;
    }
    try {
      return source.getMessage(code, args, locale);
    } catch (NoSuchMessageException ex) {
      return code;
    }
  }
}
