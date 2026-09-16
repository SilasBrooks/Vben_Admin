package com.vben.service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI（Swagger）文档全局配置：接口分组由各 Controller 的 @Tag 声明，
 * 此处补充文档信息与 Bearer 认证方案（Swagger UI 右上角 Authorize 填 accessToken 即可调试受保护接口）。
 */
@Configuration
public class OpenApiConfig {

  private static final String SCHEME_NAME = "bearerAuth";

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("Vben Admin 后端接口文档")
            .description("Spring Boot 3 + JWT 双 Token + 按钮级 RBAC 后台服务。"
                + "登录接口 /auth/login 获取 accessToken 后，点击 Authorize 填入即可调试受保护接口。")
            .version("1.0.0"))
        .components(new Components().addSecuritySchemes(SCHEME_NAME,
            new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME));
  }
}
