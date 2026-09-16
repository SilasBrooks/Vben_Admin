# OpenAPI 文档能力规格（platform/api-docs）—— 主规格

## Purpose

为全部 REST 接口提供自动生成的 OpenAPI 3 文档与交互式 Swagger UI，支撑前后端协作、联调调试与新成员上手；文档端点在开发环境开放、生产环境关闭。

## Requirements

### Requirement: API 文档自动生成

系统 MUST 通过 springdoc 自动生成 OpenAPI 3 规范文档，JSON 端点为 `/api/v3/api-docs`，交互式 UI 为 `/api/swagger-ui/index.html`。文档 MUST 按业务域分组（认证授权、系统用户/角色/菜单/部门/数据字典、登录/操作日志、AI 助手），并 MUST 声明全局 Bearer 认证方案，使 Swagger UI 中可直接携带 accessToken 调试受保护接口。

#### Scenario: 开发者访问 Swagger UI 调试接口

- **WHEN** 开发者打开 `/api/swagger-ui/index.html` 并在 Authorize 中填入登录返回的 accessToken
- **THEN** 后续在 UI 中调用受保护接口时请求头自动携带 `Authorization: Bearer <token>`

### Requirement: 文档路径放行与生产关闭

文档端点 MUST 加入 JwtAuthFilter 白名单（无需登录即可查看）；同时 MUST 提供 `springdoc.api-docs.enabled` 开关，生产 profile MUST 关闭文档端点（关闭后返回 404）。

#### Scenario: 未登录可查看文档、生产环境不可访问

- **WHEN** 未携带 token 访问文档端点
- **THEN** dev 环境返回 200；生产环境返回 404
