# 任务清单：add-openapi-docs

- [x] 1.1 pom 引入 springdoc-openapi-starter-webmvc-ui；OpenApiConfig（全局信息 + bearerAuth securityScheme）
- [x] 1.2 application.yml 配置（分组/开关）；prod profile 关闭；JwtAuthFilter 白名单加 /v3/api-docs/** 与 /swagger-ui/**
- [x] 1.3 主要 Controller 加 @Tag；AuthController 关键接口加 @Operation（captcha/login/refresh/logout/codes）
- [x] 2.1 重启验证：/api/v3/api-docs 返回 JSON、/api/swagger-ui/index.html 可打开
- [x] 2.2 同步主 spec → 归档 → README 更新
