# platform/dashboard-stats 变更 delta

## MODIFIED Requirements

### Requirement: 仪表盘聚合接口

系统 SHALL 提供 `GET /api/dashboard/summary` 聚合接口，登录即可访问（不要求任何权限码），单次响应返回以下数据块：

- `totals`：用户总数、角色总数、部门总数、当前在线人数、文件总数、字典类型总数
- `today`：今日登录成功次数、今日登录失败次数、今日操作次数
- `loginTrend`：近 14 天每日登录成功/失败次数序列（日期升序）
- `deptDistribution`：各部门人数分布（部门名 + 人数）
- `moduleDistribution`：近 14 天操作日志按模块聚合的 Top5（模块名 + 次数）
- `recentLogins`：最近 8 条登录记录（用户名、IP、时间、成败）
- `recentOpers`：最近 8 条操作记录（操作人、模块、动作、耗时、成败）
- `aiUsage`：AI 用量统计——累计/今日/本月的 token 总量及调用次数、近 14 天每日 token 合计与调用次数序列（日期升序）、近 30 天按模型聚合 token Top5（模型名 + tokens + 次数）

#### Scenario: 登录用户获取仪表盘数据

- **WHEN** 任意已登录用户（含无系统管理权限的普通角色）携带有效 token 请求 `GET /api/dashboard/summary`
- **THEN** 返回 200 与上述全部数据块（含 `aiUsage`）
- **AND** 数据均为聚合统计或非敏感摘要，不含密码、参数原文等敏感明细

#### Scenario: 未认证访问被拒绝

- **WHEN** 未携带有效 token 请求该接口
- **THEN** 返回 401

### Requirement: 工作台页面展示真实数据

工作台页面（/workspace）SHALL 展示：欢迎横幅（按当前时段问候 + 当前用户昵称 + 今日日期）、7 张统计卡（用户/角色/部门/在线用户/文件/今日登录/今日 AI Token；前 6 张点击跳转对应管理页，今日 AI Token 卡展示当日 token 总量并点击跳转分析页）、近 14 天登录趋势折线图与近 14 天 AI Token 用量趋势图（左右并排）、快捷入口导航（MUST 仅展示当前用户有权限访问的常用页面，按既有权限码用 `v-access:code` 控制显隐）、最近登录列表（成败用 tag 标色），数据全部来自 summary 接口。

#### Scenario: 用户打开工作台

- **WHEN** 已登录用户访问 /workspace
- **THEN** 页面渲染横幅、7 张统计卡（含今日 AI Token）、双趋势图、快捷入口与最近登录列表，数值与数据库实际数据一致
- **AND** 普通角色（无系统管理权限）同样可见聚合统计，快捷入口仅显示其有权访问的页面

#### Scenario: 无 AI 调用记录时打开工作台

- **WHEN** 系统尚无任何 LLM 调用记录，已登录用户访问 /workspace
- **THEN** 今日 AI Token 卡显示 0，AI Token 趋势图渲染空态，页面其余部分正常

### Requirement: 分析页展示真实数据

分析页（/analytics）SHALL 展示：6 张概览卡（总用户/总角色/累计登录/累计操作/累计 AI 调用/累计 AI Token）、近 14 天 AI Token 用量趋势图（每日 token 柱状 + 调用次数折线组合）、AI 模型用量分布环形图与部门人数分布饼图（左右并排）、最近操作记录列表（含耗时与状态 tag），数据全部来自 summary 接口。

#### Scenario: 用户打开分析页

- **WHEN** 已登录用户访问 /analytics
- **THEN** 页面渲染 6 张概览卡、AI Token 趋势图、模型分布与部门分布图、最近操作列表，数值与数据库实际数据一致

#### Scenario: 无 AI 调用记录时打开分析页

- **WHEN** 系统尚无任何 LLM 调用记录，已登录用户访问 /analytics
- **THEN** AI 相关概览卡显示 0，AI 图表渲染空态，系统运营统计部分正常展示
