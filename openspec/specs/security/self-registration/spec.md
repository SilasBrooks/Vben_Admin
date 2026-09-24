# security/self-registration Specification

## Purpose
限定后台账号由管理员统一创建，关闭公开自助注册页面、入口和接口，防止遗留公开路径绕过管理员授权创建后台账号；同时保持已有账号、管理员用户管理与登录验证码正常可用。

## Requirements

### Requirement: 禁止公开自助注册

后台系统 MUST NOT 提供公开创建账号的接口或注册页面。POST /api/auth/register MUST 从 Controller 和 JWT 公开路径中移除，OpenAPI MUST NOT 发布该接口。账号创建 MUST 通过现有管理员用户管理能力完成；登录验证码和既有账号 MUST 保持可用。

#### Scenario: 匿名调用旧注册接口

- **WHEN** 匿名客户端请求 POST /api/auth/register
- **THEN** 请求被认证过滤器拒绝，不创建账号

#### Scenario: 公开接口文档

- **WHEN** 获取当前 OpenAPI 定义
- **THEN** 不包含 /auth/register 的操作

#### Scenario: 登录验证码继续可用

- **WHEN** 用户获取登录验证码
- **THEN** /api/auth/captcha 按原有规则返回一次性图形验证码
