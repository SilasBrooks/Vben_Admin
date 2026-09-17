# platform/dashboard-stats 规格增量

## ADDED Requirements

### Requirement: 仪表盘聚合接口

系统 SHALL 提供 `GET /api/dashboard/summary` 聚合接口，登录即可访问（不要求任何权限码），单次响应返回以下数据块：

- `totals`：用户总数、角色总数、部门总数、当前在线人数、文件总数、字典类型总数
- `today`：今日登录成功次数、今日登录失败次数、今日操作次数
- `loginTrend`：近 14 天每日登录成功/失败次数序列（日期升序）
- `deptDistribution`：各部门人数分布（部门名 + 人数）
- `moduleDistribution`：近 14 天操作日志按模块聚合的 Top5（模块名 + 次数）
- `recentLogins`：最近 8 条登录记录（用户名、IP、时间、成败）
- `recentOpers`：最近 8 条操作记录（操作人、模块、动作、耗时、成败）

#### Scenario: 登录用户获取仪表盘数据

- **WHEN** 任意已登录用户（含无系统管理权限的普通角色）携带有效 token 请求 `GET /api/dashboard/summary`
- **THEN** 返回 200 与上述全部数据块
- **AND** 数据均为聚合统计或非敏感摘要，不含密码、参数原文等敏感明细

#### Scenario: 未认证访问被拒绝

- **WHEN** 未携带有效 token 请求该接口
- **THEN** 返回 401

### Requirement: 工作台页面展示真实数据

工作台页面（/workspace）SHALL 展示：欢迎横幅（按当前时段问候 + 当前用户昵称 + 今日日期）、6 张统计卡（用户/角色/部门/在线用户/文件/今日登录，点击跳转对应管理页）、近 14 天登录趋势折线图（成功/失败双线）、最近登录列表（成败用 tag 标色），数据全部来自 summary 接口。

#### Scenario: 用户打开工作台

- **WHEN** 已登录用户访问 /workspace
- **THEN** 页面渲染横幅、统计卡、趋势图与最近登录列表，数值与数据库实际数据一致
- **AND** 普通角色（无系统管理权限）同样可见

### Requirement: 分析页展示真实数据

分析页（/analytics）SHALL 展示：4 张概览卡（总用户/总角色/累计登录/累计操作）、近 14 天登录趋势柱线组合图、部门人数分布饼图与操作模块分布环形图（左右并排）、最近操作记录列表（含耗时与状态 tag）。

#### Scenario: 用户打开分析页

- **WHEN** 已登录用户访问 /analytics
- **THEN** 页面渲染概览卡、三组图表与最近操作列表，数值与数据库实际数据一致

### Requirement: 移除模板演示组件

旧的仪表盘演示数据 SHALL 被移除：`analytics-*.vue` 5 个 demo 子组件删除，workspace/analytics 不再引用 `@vben/common-ui` 的 AnalysisChartCard/WorkbenchProject/WorkbenchQuickNav/WorkbenchTodo/WorkbenchTrends 等演示组件，且页面无残留死引用。

#### Scenario: 演示组件清理完成

- **WHEN** 全局搜索 `analytics-visits|analytics-trends|WorkbenchProject|AnalysisChartsTabs`
- **THEN** `views/dashboard/` 下无任何残留引用，构建无报错
