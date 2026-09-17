# Tasks: add-dashboard-stats

## 1. 后端

- [ ] 1.1 `module/dashboard/DashboardController.java`：GET /dashboard/summary，登录即可，@Tag 文档
- [ ] 1.2 `module/dashboard/DashboardService.java`：totals/today/loginTrend/deptDistribution/moduleDistribution/recentLogins/recentOpers 聚合实现
- [ ] 1.3 `mvn compile` 通过

## 2. 前端

- [ ] 2.1 `api/dashboard.ts`：DashboardSummary 类型 + getDashboardSummaryApi
- [ ] 2.2 重写 `views/dashboard/workspace/index.vue`：横幅 + 6 卡 + 登录趋势折线图 + 最近登录列表
- [ ] 2.3 重写 `views/dashboard/analytics/index.vue`：4 概览卡 + 柱线图 + 双饼图并排 + 最近操作列表
- [ ] 2.4 删除 5 个 analytics-*.vue demo 子组件，清理死引用

## 3. 验证

- [ ] 3.1 重启后端；curl 断言 summary 字段与数值（vben 200 / jack 200 / 无 token 401）
- [ ] 3.2 浏览器冒烟：vben 两页渲染（图表出现、卡片数值真实）；jack 同样可见

## 4. 收尾

- [ ] 4.1 README 功能清单补一行
- [ ] 4.2 openspec validate --strict → 归档 → commit + push（gy, gy:master）→ 更新记忆
