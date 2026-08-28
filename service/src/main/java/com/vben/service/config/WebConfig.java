package com.vben.service.config;

import com.vben.service.security.JwtAuthFilter;
import com.vben.service.security.PermissionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：JWT 过滤器注册 + 权限拦截器 + CORS
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

  private final JwtAuthFilter jwtAuthFilter;
  private final PermissionInterceptor permissionInterceptor;
  private final VbenProperties properties;

  @Bean
  public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration() {
    FilterRegistrationBean<JwtAuthFilter> registration =
        new FilterRegistrationBean<>(jwtAuthFilter);
    // 尽量靠前，保证业务代码尽早拿到 LoginUser
    registration.setOrder(1);
    return registration;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(permissionInterceptor)
        .addPathPatterns("/**")
        .excludePathPatterns("/error", "/h2-console/**");
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
        // 允许跨域携带 Cookie（refreshToken），不能为 *，必须显式来源
        .allowedOriginPatterns(properties.getCors().getAllowedOrigins().toArray(String[]::new))
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        .allowedHeaders("*")
        .allowCredentials(true)
        .maxAge(3600);
  }
}
