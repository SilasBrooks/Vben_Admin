# system/monitor/oper-log Specification

## Purpose

为管理端写操作提供可追溯的审计记录：谁在什么时间对什么资源做了什么操作、结果如何、耗时多少，用于问题回溯与安全审计。

## Requirements

### Requirement: 声明式操作日志记录
系统 SHALL 提供 `@OperLog` 注解，标注的 controller 方法被调用时 MUST 异步记录一条操作日志，内容包含：操作人、所属模块、操作描述、调用方法、请求方式、请求 URL、入参摘要、执行结果（成功/失败）、失败错误信息、耗时、IP、操作时间。入参摘要 MUST 脱敏敏感字段（password 类）并截断至 2000 字符。

#### Scenario: 成功操作被记录
- **WHEN** 标注了 `@OperLog` 的接口执行成功
- **THEN** 生成一条 status 为成功的操作日志，含操作人、模块、耗时等完整字段

#### Scenario: 失败操作被记录
- **WHEN** 标注了 `@OperLog` 的接口抛出异常
- **THEN** 生成一条 status 为失败的操作日志，error_msg 记录异常摘要

#### Scenario: 日志写入失败不影响业务
- **WHEN** 操作日志落库失败
- **THEN** 原接口的响应与结果不受影响

### Requirement: 操作日志分页查询
系统 SHALL 提供操作日志分页查询接口，支持按操作人模糊、状态、时间范围过滤；接口 MUST 要求 `Monitor:OperLog:List` 权限码。

#### Scenario: 无权限用户查询
- **WHEN** 不具备 `Monitor:OperLog:List` 权限的用户请求操作日志列表
- **THEN** 返回 403 拒绝

### Requirement: 操作日志删除与清空
系统 SHALL 提供单条删除与清空全部接口，均 MUST 要求 `Monitor:OperLog:Delete` 权限码。

#### Scenario: 清空操作日志
- **WHEN** 具备删除权限的用户调用清空接口
- **THEN** 操作日志表被清空
