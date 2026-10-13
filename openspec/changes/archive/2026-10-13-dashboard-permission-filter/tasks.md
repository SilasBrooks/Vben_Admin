# dashboard-permission-filter 任务清单

## 1. 后端

- [x] 1.1 `DashboardService.summary` 装配按权限过滤：OperLog 码控制 recentOpers/moduleDistribution/today.operCount/totals.operCount，LoginLog 码控制 recentLogins/loginTrend/today.loginSuccess/today.loginFail/totals.loginCount，无权限装配为 null
- [x] 1.2 单测：super 全可见、无监控权限受控字段为 null 且 aiUsage/totals 其余字段不受影响

## 2. 前端

- [x] 2.1 `api/dashboard.ts`：受控字段改可空类型（recentLogins/recentOpers/loginTrend/moduleDistribution、today 三计数、totals.loginCount/operCount）
- [x] 2.2 工作台：今日概况卡过滤 null 指标；登录趋势图与最近登录区块按数据 null 显隐
- [x] 2.3 分析页：概览卡过滤 null 指标；模块分布图与最近操作区块按数据 null 显隐（模型分布维持空态逻辑不变）
- [x] 2.4 `pnpm check:type` 通过

## 3. 验证与收尾

- [x] 3.1 `mvn test` 全绿
- [x] 3.2 E2E：admin 全可见；stockAdmin（无监控权限）确认受控字段返回 null 且页面不渲染对应卡/图/表
- [x] 3.3 README 仪表盘行同步
- [x] 3.4 `openspec validate dashboard-permission-filter --strict` 通过 → `openspec archive dashboard-permission-filter --yes`（归档前不 commit）
