## Context

现有模式已稳定：后端 `XxxAdminService` + controller + `@RequirePermission`（纯 MyBatis-Plus，无外键），前端 `useVbenVxeGrid` 分页列表 + 搜索表单。日志模块完全复用这些模式。登录逻辑集中在 `AuthController.login`（单入口，成功/失败路径清晰），埋点成本低。

## Goals / Non-Goals

- Goals：管理端写操作可审计（谁/何时/对什么/干了什么/结果/耗时）；登录尝试全记录；两个查询页面 + 清理能力
- Non-Goals：不做日志导出 Excel、不做日志归档分表、不做 ES/链路追踪；注解只标 controller 写操作，查询接口不记录（避免噪音）

## Decisions

### D1：声明式注解 + AOP，异步落库
- `@OperLog(module = "用户管理", description = "新增用户")` 标在 controller 方法上，`@Around` 切面统一采集
- 采集后提交到独立单线程 executor（守护线程 + 静默吞异常），不阻塞业务响应；**日志写入失败绝不影响业务**（try-catch 兜底）
- 操作人（userId/username）在请求线程内从 `LoginUserHolder` 取出后再异步，避免线程上下文丢失

### D2：入参摘要化 + 截断
- 入参序列化请求 body（过滤 password/newPassword 等敏感字段，替换为 `***`）；截断 2000 字符
- errorMsg 截断 2000 字符；避免大参数拖垮库

### D3：两表结构（无外键，纯 MP 风格）
- `sys_oper_log`：id、oper_user_id、oper_name、module、description、method(class#method)、request_method、request_url、params、status(0成功1失败)、error_msg、ip、cost_ms、oper_time
- `sys_login_log`：id、username、status(0成功1失败)、message、ip、user_agent、login_time
- IP 提取：X-Forwarded-For 首段 → 降级 remoteAddr（工具方法放 common）

### D4：登录埋点在 AuthController.login
- 成功/失败两条路径各调 `LoginLogService.record()`（异步）；失败记录用户名用入参原文（用户可能不存在）

### D5：接口与权限码
- `GET /monitor/oper-log/list`（`Monitor:OperLog:List`）、`DELETE /monitor/oper-log/{id}`、`DELETE /monitor/oper-log/clear`
- `GET /monitor/login-log/list`（`Monitor:LoginLog:List`）、`DELETE /monitor/login-log/{id}`、`DELETE /monitor/login-log/clear`
- list 过滤：操作人/用户名模糊、状态、时间范围（beginTime/endTime）

### D6：菜单种子
- 「系统监控」M 目录（order 9500，`Monitor` 图标）→ 「操作日志」「登录日志」C 菜单 + 各 2 个 F 按钮；authority `super,admin`，systemSet 收录（user 不可见）

### D7：前端页面
- 两个列表页复用 useVbenVxeGrid 分页 + 搜索（搜索字段 ≤1 行，不开启 showCollapseButton）；操作列仅「删除」，工具栏「清空日志」需二次确认
- 时间范围用 DatePicker range，提交前拆为 beginTime/endTime

## Risks / Trade-offs

- 单线程异步队列在极端高峰会积压 —— 当前单体规模可接受；队列满时丢弃新日志（保业务）
- 入参序列化对超大 body 有开销 —— 已截断 + 只标写操作，可控

## Migration Plan

1. H2/MySQL schema 加两表 → 实体/Mapper/Service/Controller → 注解+切面 → 登录埋点 → 种子
2. 前端 API + 两个页面 → typecheck
3. 重置 H2 → 验收场景逐条过

## Open Questions

- 无
