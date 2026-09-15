# monitor/login-log Delta

## ADDED Requirements

### Requirement: 登录尝试记录
系统 MUST 对每次登录尝试（成功与失败）记录一条登录日志，内容包含：尝试的用户名、结果（成功/失败）、消息、IP、User-Agent、登录时间。用户名不存在时的失败尝试 MUST 同样被记录。

#### Scenario: 登录成功被记录
- **WHEN** 用户使用正确凭据登录成功
- **THEN** 生成一条 status 为成功的登录日志，含 IP 与 UA

#### Scenario: 登录失败被记录
- **WHEN** 用户名不存在或密码错误导致登录失败
- **THEN** 生成一条 status 为失败的登录日志，message 说明失败原因

### Requirement: 登录日志分页查询
系统 SHALL 提供登录日志分页查询接口，支持按用户名模糊、状态、时间范围过滤；接口 MUST 要求 `Monitor:LoginLog:List` 权限码。

#### Scenario: 无权限用户查询
- **WHEN** 不具备 `Monitor:LoginLog:List` 权限的用户请求登录日志列表
- **THEN** 返回 403 拒绝

### Requirement: 登录日志删除与清空
系统 SHALL 提供单条删除与清空全部接口，均 MUST 要求 `Monitor:LoginLog:Delete` 权限码。

#### Scenario: 清空登录日志
- **WHEN** 具备删除权限的用户调用清空接口
- **THEN** 登录日志表被清空

## Purpose

记录每一次登录尝试，用于发现暴力破解、异常来源登录等安全事件，并为登录失败限流（后续变更）提供数据基础。
