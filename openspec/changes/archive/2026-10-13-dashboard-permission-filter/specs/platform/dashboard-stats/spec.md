# dashboard-permission-filter Delta

## MODIFIED Requirements

### Requirement: 仪表盘聚合接口

系统 SHALL 提供 `GET /api/dashboard/summary` 聚合接口，登录即可访问（不要求任何权限码），单次响应返回以下数据块；其中监控类数据块 MUST 按登录用户权限码在服务端过滤（无权限时字段返回 null，MUST NOT 下发对应数据）：

- `totals`：用户总数、角色总数、部门总数、当前在线人数、文件总数、字典类型总数（全员可见）；累计登录记录数受 `Monitor:LoginLog:List` 控制、累计操作日志数受 `Monitor:OperLog:List` 控制，无对应权限时 MUST 返回 null
- `today`：今日登录成功/失败次数受 `Monitor:LoginLog:List` 控制、今日操作次数受 `Monitor:OperLog:List` 控制，无对应权限时对应字段 MUST 返回 null
- `loginTrend`：近 14 天每日登录成功/失败次数序列（日期升序），受 `Monitor:LoginLog:List` 控制，无权限时 MUST 返回 null
- `deptDistribution`：各部门人数分布（部门名 + 人数，全员可见）
- `moduleDistribution`：近 14 天操作日志按模块聚合的 Top5（模块名 + 次数），受 `Monitor:OperLog:List` 控制，无权限时 MUST 返回 null
- `recentLogins`：最近 8 条登录记录（用户名、IP、时间、成败），受 `Monitor:LoginLog:List` 控制，无权限时 MUST 返回 null
- `recentOpers`：最近 8 条操作记录（操作人、模块、动作、耗时、成败），受 `Monitor:OperLog:List` 控制，无权限时 MUST 返回 null
- `aiUsage`：AI 用量统计——累计/今日/本月的 token 总量及调用次数、近 14 天每日 token 合计与调用次数序列（日期升序）、近 30 天按模型聚合 token Top5（模型名 + tokens + 次数），全员可见

#### Scenario: 登录用户获取仪表盘数据

- **WHEN** 任意已登录用户（含无系统管理权限的普通角色）携带有效 token 请求 `GET /api/dashboard/summary`
- **THEN** 返回 200；全员可见数据块（totals 非监控计数、deptDistribution、aiUsage）正常返回
- **AND** 数据均为聚合统计或非敏感摘要，不含密码、参数原文等敏感明细

#### Scenario: 无监控权限的用户获取受控数据块

- **WHEN** 不具备 `Monitor:OperLog:List` 与/或 `Monitor:LoginLog:List` 权限的已登录用户请求 `GET /api/dashboard/summary`
- **THEN** 受控字段（recentOpers、moduleDistribution、recentLogins、loginTrend 及 today/totals 中对应计数字段）返回 null，响应中 MUST NOT 出现对应监控数据
- **AND** 其余全员可见数据块正常返回

#### Scenario: 具备监控权限的用户获取完整数据

- **WHEN** 同时具备 `Monitor:OperLog:List` 与 `Monitor:LoginLog:List` 权限的已登录用户请求 `GET /api/dashboard/summary`
- **THEN** 全部数据块正常返回，数值与数据库实际数据一致

#### Scenario: 未认证访问被拒绝

- **WHEN** 未携带有效 token 请求该接口
- **THEN** 返回 401

### Requirement: 工作台页面展示真实数据

工作台页面（/workspace）SHALL 以「今日视角 + 操作入口」定位展示：欢迎横幅（按当前时段问候 + 当前用户昵称 + 今日日期）、今日概况卡（今日登录成功、今日登录失败、今日操作、当前在线用户、今日 AI 调用、今日 AI Token；各卡可点击跳转对应管理页，AI 相关两卡跳转分析页；受监控权限控制的指标在接口返回 null 时 MUST NOT 渲染对应卡片）、近 14 天登录趋势折线图（受 `Monitor:LoginLog:List` 控制的数据，接口返回 null 时 MUST 隐藏该图表区块）、快捷入口导航（MUST 仅展示当前用户有权限访问的常用页面，按既有权限码用 `v-access:code` 控制显隐）、最近登录列表（受 `Monitor:LoginLog:List` 控制，接口返回 null 时 MUST 隐藏该列表区块，成败用 tag 标色），数据全部来自 summary 接口；工作台 MUST NOT 展示累计类规模指标卡（总用户/总角色等累计数据由分析页承载）。

#### Scenario: 用户打开工作台

- **WHEN** 已登录用户访问 /workspace
- **THEN** 页面渲染横幅、有权限的今日概况卡、登录趋势图、快捷入口与最近登录列表，数值与数据库实际数据一致
- **AND** 普通角色（无系统管理权限）同样可见非监控的今日概况统计，快捷入口仅显示其有权访问的页面

#### Scenario: 无监控权限的用户打开工作台

- **WHEN** 不具备 `Monitor:LoginLog:List` 与 `Monitor:OperLog:List` 权限的已登录用户访问 /workspace
- **THEN** 今日登录成功/失败/今日操作卡、登录趋势图、最近登录列表不渲染，页面仅展示当前在线、今日 AI 调用/Token 卡与快捷入口，无报错

#### Scenario: 无 AI 调用记录时打开工作台

- **WHEN** 系统尚无任何 LLM 调用记录，已登录用户访问 /workspace
- **THEN** 今日 AI 调用与今日 AI Token 卡显示 0，页面其余部分正常

### Requirement: 分析页展示真实数据

分析页（/analytics）SHALL 以「全局洞察」定位展示：概览卡（总用户/总角色/累计登录/累计操作/累计 AI 调用/累计 AI Token；其中累计登录受 `Monitor:LoginLog:List`、累计操作受 `Monitor:OperLog:List` 控制，接口返回 null 时 MUST NOT 渲染对应卡片）、近 14 天 AI Token 用量趋势图（每日 token 柱状 + 调用次数折线组合，全站唯一 AI 趋势图）、三张分布图（部门人数分布饼图——全员可见；AI 模型用量分布环形图——无调用记录时展示空态；近 14 天操作模块分布 Top5 环形图——受 `Monitor:OperLog:List` 控制，接口返回 null 时 MUST 隐藏该图卡片）、最近操作记录列表（受 `Monitor:OperLog:List` 控制，接口返回 null 时 MUST 隐藏该列表区块，含耗时与状态 tag），数据全部来自 summary 接口。

#### Scenario: 用户打开分析页

- **WHEN** 已登录用户访问 /analytics
- **THEN** 页面渲染有权限的概览卡、AI Token 趋势图、部门/模型/模块分布图与最近操作列表，数值与数据库实际数据一致

#### Scenario: 无监控权限的用户打开分析页

- **WHEN** 不具备 `Monitor:OperLog:List` 与 `Monitor:LoginLog:List` 权限的已登录用户访问 /analytics
- **THEN** 累计登录/累计操作卡、模块分布图、最近操作列表不渲染，页面仍展示总用户/总角色/AI 概览卡、AI Token 趋势图与部门/模型分布图，无报错

#### Scenario: 无 AI 调用记录时打开分析页

- **WHEN** 系统尚无任何 LLM 调用记录，已登录用户访问 /analytics
- **THEN** AI 相关概览卡显示 0，模型分布图展示空态，部门分布图与有权限的其余部分正常展示
