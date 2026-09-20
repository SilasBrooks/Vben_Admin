package com.vben.service.common;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * 后端 i18n 装配：错误消息走 messages.properties（中文兜底）+ messages_en_US.properties。
 *
 * <p>语言按 Accept-Language 请求头解析（前端 requestClient 每请求携带），无 header 回落
 * zh_CN——保证 curl/E2E/服务间调用的确定性输出。bean validation 的 {key} 占位符
 * 同样从该 MessageSource 解析（校验注解 message 写 "{error.xxx}" 即可）。
 */
@Configuration
public class I18nConfig {

  public I18nConfig(MessageSource messageSource) {
    I18nMessage.init(messageSource);
  }

  /** Accept-Language 解析器：无 header 时回落简体中文（DispatcherServlet 自动启用） */
  @Bean
  public LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
    return resolver;
  }

  /** 让 bean validation 的 {key} 占位符从应用 MessageSource 解析 */
  @Bean
  public LocalValidatorFactoryBean defaultValidator(MessageSource messageSource) {
    LocalValidatorFactoryBean bean = new LocalValidatorFactoryBean();
    bean.setValidationMessageSource(messageSource);
    return bean;
  }
}
