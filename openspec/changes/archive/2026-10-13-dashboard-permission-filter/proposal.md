# dashboard-permission-filter

## Why

仪表盘当前「登录即可全员同版」，summary 接口把全系统监控数据原样返回给任意登录用户：最近操作（他人操作明细）、操作模块分布、最近登录（他人用户名与 IP）、登录趋势均无权限校验。操作日志/登录日志页本身挂有 `Monitor:OperLog:List` / `Monitor:LoginLog:List` 权限码，仪表盘绕过了权限语义，普通用户可看到不该看到的监控数据——用户反馈「没有权限的用户，有些模块不该显示」。

## What Changes

- 后端 summary 按登录用户权限码过滤监控类数据块：`Monitor:OperLog:List` 控制 recentOpers / moduleDistribution / today.operCount / totals.operCount；`Monitor:LoginLog:List` 控制 recentLogins / loginTrend / today.loginSuccess / today.loginFail / totals.loginCount；无对应权限时相关字段返回 null（不再返回数据）；用户/角色/部门/在线/文件/字典计数、部门分布、aiUsage 保持全员可见
- 前端两页联动显隐：今日概况卡 / 概览卡中权限缺失的指标卡不渲染；登录趋势图、最近登录、模块分布图、最近操作列表在数据为 null 时整块隐藏
- `GET /dashboard/summary` 本身仍登录即可（返回结构不变，仅受控字段可能为 null），无新增接口

## Capabilities

- MODIFIED platform/dashboard-stats：聚合接口需求（权限过滤规则）、工作台页需求、分析页需求（受控块显隐）

## Impact

- 后端：`DashboardService`（读取 LoginUser 权限码集合过滤装配）+ 单测
- 前端：`api/dashboard.ts` 类型改 optional 字段、`views/dashboard/workspace/index.vue`、`views/dashboard/analytics/index.vue`
- README 注意事项/仪表盘行同步
