# system/permission-cache Specification

## Purpose
TBD - created by archiving change performance-optimization. Update Purpose after archive.

## Requirements

### Requirement: 认证权限快照缓存

认证过滤器 SHALL 按 `userId` 将权限快照（用户名、角色键集合、权限码集合）缓存于 Redis（key 纳入 `vben:` 前缀统一管理，TTL 不超过 5 分钟作为兜底）。缓存命中时 MUST 跳过数据库权限装配查询；未命中 MUST 回源查库并以与现状一致的语义装配（含用户 `status` 有效性校验），查询结果回填缓存。

缓存 MUST 提供三级主动失效：用户级变更（分配角色、禁用、重置密码等）MUST 即时失效该用户；角色级变更（角色菜单授权、角色状态）MUST 即时失效该角色下全部用户；菜单级变更（菜单增删改、状态）MUST 即时全量失效。失效 MUST NOT 依赖 TTL 过期。

Redis 异常时权限装配 MUST 降级为直查数据库（与无缓存时语义完全一致），MUST NOT 因缓存不可用拒绝请求；token 版本号校验的 fail-closed 语义 MUST NOT 因本缓存而改变。缓存内容 MUST NOT 包含敏感凭据（密码散列等）。

#### Scenario: 命中缓存跳过查库

- **WHEN** 同一用户连续发起两个请求，首个请求已完成权限装配且回填缓存
- **THEN** 第二个请求的认证过程不再执行用户/角色/权限三条装配 SQL，鉴权结果一致

#### Scenario: 用户级变更即时失效

- **WHEN** 管理员禁用某用户或重置其密码（既有 tokenVersion bump 点）
- **THEN** 该用户权限快照缓存被即时删除，后续请求以库中最新数据装配

#### Scenario: 角色级变更失效角色下用户

- **WHEN** 管理员修改某角色的菜单授权
- **THEN** 拥有该角色的全部用户权限缓存被即时失效，其权限码在下次请求即反映新授权

#### Scenario: 菜单级变更全量失效

- **WHEN** 管理员修改任意菜单（含状态切换）
- **THEN** 全部权限快照缓存被清理，所有用户下次请求重新装配

#### Scenario: Redis 异常降级

- **WHEN** Redis 不可用时用户发起请求
- **THEN** 认证过滤器直查数据库完成权限装配，请求正常处理；token 版本号校验仍按 fail-closed 拒绝无法校验的请求
