# Design: add-dashboard-stats

## 后端（module/dashboard）

- `DashboardController`：`GET /dashboard/summary`，不标 `@RequirePermission`（登录即可），类注释说明"全员同版聚合数据"
- `DashboardService`：聚合查询，全部用 MyBatis-Plus（LambdaQueryWrapper/selectMaps groupBy），不写 XML：
  - totals：`userMapper.selectCount` 等 6 个 count；在线人数取 `OnlineSessionService.listAll().size()`
  - today/loginTrend：`sys_login_log` 按 `login_time >= 今日/14天前` 查询后在内存按日期分组（演示规模数据量小，避免写数据库方言相关的日期函数 SQL）
  - deptDistribution：`sys_user` groupBy dept_id 后用 `SysDeptMapper` 回填部门名
  - moduleDistribution：`sys_oper_log` 近 14 天 selectMaps groupBy module 取 Top5
  - recentLogins/recentOpers：各自 mapper 按时间倒序 limit 8
- 响应用既有 `R<>` 包装；字段用 LinkedHashMap 保序，前端 TS 接口与后端字段一一对应

## 前端

- `api/dashboard.ts`：`DashboardSummary` 类型 + `getDashboardSummaryApi()`
- `views/dashboard/workspace/index.vue`：欢迎横幅（ElCard 渐变背景）+ 6 张 `ElCard` 统计卡（`router.push` 跳转 /system/user 等）+ EchartsUI 折线图 + 最近登录 ElTable；时段问候（凌晨/上午/下午/晚上）
- `views/dashboard/analytics/index.vue`：4 张概览卡 + 柱线组合图（柱=成功、线=失败或双柱）+ 饼图/环形图并排 + 最近操作 ElTable
- 图表统一 `@vben/plugins/echarts` 的 `EchartsUI` + `useEcharts`，随 summary 数据一次性渲染；窗口自适应由 EchartsUI 自带 resize 处理
- 删除 `analytics-visits.vue / analytics-visits-source.vue / analytics-visits-sales.vue / analytics-visits-data.vue / analytics-trends.vue`；workspace 移除对 `@vben/common-ui` 演示组件与 `../analytics/*` 的引用

## 验证策略

- curl：登录 vben → GET /dashboard/summary 断言各数据块字段存在且数值正确（与 SQL count 对照）；jack 登录同接口 200；无 token 401
- 浏览器冒烟：vben 打开两页截图验证渲染（横幅/卡片/图表/列表），jack 访问同样可见
- `openspec validate --strict` → README 补行 → 归档 → commit/push
