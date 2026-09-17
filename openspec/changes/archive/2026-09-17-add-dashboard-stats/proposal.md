# Proposal: add-dashboard-stats

## Why

当前工作台（/workspace）与分析页（/analytics）仍是 Vben 模板演示数据（Github/Vue 项目卡片、虚构的用户量/访问量），与系统真实业务（用户/角色/部门/日志/在线用户/文件）完全脱节，对简历展示和实际使用都没有价值。

## What Changes

1. 后端新增 `module/dashboard` 模块：`GET /api/dashboard/summary` 单接口返回全部仪表盘聚合数据，**登录即可访问**（不挂权限码，全员同版），只返回聚合数字与近 14 天趋势，不含敏感明细
2. 前端重写工作台：欢迎横幅 + 6 张统计卡（可点击跳转对应管理页）+ 登录趋势折线图 + 最近登录列表
3. 前端重写分析页：4 张概览卡 + 登录趋势柱线组合图 + 部门人数分布饼图 + 操作模块分布环形图 + 最近操作列表
4. 图表采用 monorepo 自带 `@vben/plugins/echarts`（EchartsUI），布局用 ElCard 自排；删除旧的 5 个 `analytics-*.vue` demo 子组件及 workspace 对 `@vben/common-ui` demo 组件的引用

## Capabilities

### New

- `platform/dashboard-stats`：仪表盘真实数据统计（summary 聚合接口 + 工作台/分析页重写）

## Impact

- 后端：新增 `module/dashboard`（controller + service），无 schema 变更，无权限码/菜单变更
- 前端：重写 `views/dashboard/` 下两个页面，删除 5 个 demo 子组件
- 受益：登录后首屏即见真实系统运行状况；普通角色（jack/stockAdmin）同样可用
