# security/idempotency Specification

## Purpose
TBD - created by archiving change add-engineering-hardening. Update Purpose after archive.

## Requirements

### Requirement: 防重复提交

系统 SHALL 提供声明式防重复提交能力：受保护接口在指定时间窗内对同一请求者 MUST 只放行一次，窗口内的重复请求 MUST 被拒绝（409，提示勿重复提交），窗口结束后自动恢复放行。请求维度 MUST 为「注解标识 + 登录用户 id」，未登录场景降级为请求 IP。幂等计数 MUST 存储于 Redis（多实例共享、重启不失效、TTL 到期自动清理）。

#### Scenario: 窗口内重复提交被拒

- **WHEN** 已登录用户在 10 秒内连续两次提交 PATCH /user/profile（第二次与第一次均在窗口内）
- **THEN** 第一次成功执行，第二次返回 409 与「请勿重复提交」类提示，资料不被二次修改

#### Scenario: 窗口结束后放行

- **WHEN** 距上次成功提交超过配置的窗口时长后再次提交
- **THEN** 请求正常执行

#### Scenario: 不同用户互不影响

- **WHEN** 用户 A 刚提交成功，用户 B 立即提交相同接口
- **THEN** B 的请求正常执行（幂等维度按用户隔离）
