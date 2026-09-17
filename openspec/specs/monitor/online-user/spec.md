# monitor/online-user Specification

## Purpose
为管理员提供实时在线用户视图与主动处置手段：查看当前在线会话（用户、登录时间、IP），并可对可疑会话执行强制下线。

## Requirements

### Requirement: 在线用户列表

系统 SHALL 提供在线用户分页列表接口，展示用户名、昵称、登录时间、登录 IP；支持按用户名模糊过滤；接口 MUST 要求 `Monitor:Online:List` 权限码。列表数据 MUST 来源于 Redis 在线会话记录（实时，非落库快照）。

#### Scenario: 管理员查看在线用户

- **WHEN** 具备 `Monitor:Online:List` 权限的管理员打开在线用户页面
- **THEN** 列表展示当前所有在线用户的用户名、登录时间与 IP，且刚登录的用户出现在列表中

#### Scenario: 无权限访问被拒

- **WHEN** 不具备 `Monitor:Online:List` 权限的用户请求在线用户列表
- **THEN** 返回 403 拒绝

### Requirement: 强制下线

系统 SHALL 提供强制下线接口，MUST 要求 `Monitor:Online:Kick` 权限码；执行后目标用户 MUST 被立即登出（版本号递增 + 会话移除），且 MUST 禁止对当前登录用户本人执行强制下线。

#### Scenario: 强制下线在线用户

- **WHEN** 管理员在在线用户列表对目标用户点击"强制下线"并确认
- **THEN** 目标用户在线记录消失，其已签发 token 立即失效（下次请求 401）

#### Scenario: 禁止自踢

- **WHEN** 管理员尝试对自己执行强制下线
- **THEN** 返回 400 错误提示，不允许操作

#### Scenario: 前端按钮权限

- **WHEN** 无 `Monitor:Online:Kick` 权限的用户查看在线用户页面
- **THEN** "强制下线"按钮不渲染（v-access:code 指令隐藏）
