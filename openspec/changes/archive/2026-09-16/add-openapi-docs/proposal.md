# 变更提案：add-openapi-docs

## Why

系统已有 40+ 个 REST 接口但没有任何 API 文档。作为简历展示与实际复用项目，接口文档是硬性基础件：新人上手、前后端协作、联调排错都依赖它。成本极低（springdoc 自动扫描 + 少量注解），收益直接。

## What Changes

- 接入 `springdoc-openapi-starter-webmvc-ui`：自动生成 OpenAPI 3 JSON（`/api/v3/api-docs`）与 Swagger UI（`/api/swagger-ui/index.html`）
- 全局 OpenAPI 信息（标题/描述/版本）+ Bearer 认证方案声明，Swagger UI 可直接填 token 调试带鉴权接口
- 主要 Controller 加 `@Tag` 分组、认证与 AI 关键接口加 `@Operation` 说明
- JwtAuthFilter 白名单放行文档路径；`springdoc.api-docs.enabled` 生产可关闭（application.yml 默认开，prod profile 关）

## Capabilities

### New Capabilities

- `platform/api-docs`: OpenAPI 文档自动生成与访问控制

## Impact

- 后端：pom +1 依赖、OpenApiConfig +1 类、若干注解；无业务逻辑改动
- 前端：零改动
