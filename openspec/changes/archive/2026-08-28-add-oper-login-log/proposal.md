## Why

系统目前没有任何操作审计能力：谁在什么时间对什么接口做了什么、登录成功/失败来自哪个 IP，全部无据可查。出问题时无法追溯、无法发现暴力破解尝试。操作日志与登录日志是后台管理上线前的必备审计底座（roadmap P1-1.1）。

## What Changes

- 新增 `sys_oper_log`（操作日志表）：记录操作人、模块、操作类型、请求方法/URL、入参摘要、结果（成功/失败 + 错误信息）、耗时、IP、操作时间
- 新增 `sys_login_log`（登录日志表）：记录用户名、登录结果、IP、UA/浏览器、消息、登录时间
- 后端：`@OperLog` 注解 + AOP 切面自动落库（controller 方法上标注即记录，异步写入不阻塞业务）；登录成功/失败在认证逻辑中埋点
- 前端：`views/monitor/` 新增「操作日志」「登录日志」两个分页列表页（条件过滤：操作人/模块/状态/时间段）
- 菜单种子：「系统监控」目录 + 两个菜单 + 权限码（super/admin 可见，user 不可见）
- 不修改现有业务模块行为；日志失败不影响业务（静默降级）

## Capabilities

### New Capabilities

- `monitor/oper-log`: 操作日志能力——通过注解声明式记录管理端写操作，提供分页查询与清空，权限码 `Monitor:OperLog:List/Delete`
- `monitor/login-log`: 登录日志能力——记录每次登录尝试（成功/失败），提供分页查询与清空，权限码 `Monitor:LoginLog:List/Delete`

## Impact

- **后端**：`module/monitor/` 新增 entity/mapper/service/controller + `common/` 新增 `@OperLog` 注解与切面；`AuthService` 登录逻辑埋点；`DatabaseSeeder` 加菜单/权限种子
- **前端**：新增 `views/monitor/oper-log/index.vue`、`views/monitor/login-log/index.vue`、`api/monitor/log.ts`
- **数据**：H2/MySQL schema 各加两表（重建 H2 即可，无线上迁移）
- **依赖**：无新增第三方依赖（AOP 用 spring-boot-starter-aop，Web 已带）
