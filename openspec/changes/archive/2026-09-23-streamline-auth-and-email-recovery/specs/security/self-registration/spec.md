## REMOVED Requirements

### Requirement: 图形验证码自助注册

**Reason**：后台管理不开放自助注册。

**Migration**：由管理员在用户管理创建账号，图形验证码继续用于登录。

### Requirement: 注册唯一性及最小权限

**Reason**：公开注册接口已移除。

**Migration**：账号唯一性和角色分配沿用管理员用户管理规则。

### Requirement: 注册限流与防重复提交

**Reason**：公开注册接口已移除，不再产生注册请求。

**Migration**：保留系统既有登录防护与管理员写接口防护。

### Requirement: 前端注册与登录衔接

**Reason**：不再提供注册页面或注册成功状态。

**Migration**：管理员创建账号后，用户通过现有登录页登录。

## ADDED Requirements

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
