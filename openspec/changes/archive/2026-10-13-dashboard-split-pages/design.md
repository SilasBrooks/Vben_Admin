# dashboard-split-pages 设计

## Context

dashboard-ai-usage 归档后两页并存以下重复：AI Token 趋势图（两页同图）、累计规模卡（工作台 7 卡含用户/角色，分析页 6 卡同样含用户/角色）。summary 接口的 `today` 块（loginSuccess/loginFail/operCount）、`aiUsage.todayCalls/todayTokens`、`moduleDistribution` 均已存在并返回，差异化无需后端改动。

## Goals

- 工作台 = 今日发生了什么 + 我要去哪操作：今日概况卡、快捷入口、登录趋势、最近登录
- 分析页 = 累计怎么样 + 结构分布：累计概览卡、AI Token 趋势、三张分布图、最近操作
- 同类图表与同类指标卡全站只出现一次

## NonGoals

- 不新增后端接口或数据块，不改动 summary 协议
- 不做排行榜（Top 用户/Top 部门）等新分析维度
- 不调整路由、菜单与权限码

## Decisions

1. **今日概况卡取数**：全部来自既有 `today`（loginSuccess/loginFail/operCount）、`totals.onlineCount`、`aiUsage.todayCalls/todayTokens`，6 张卡纯前端组装。
2. **模块分布图回归分析页**：直接消费既有 `moduleDistribution` 字段（环形图，与模型分布同形态），delta spec 恢复该展示。
3. **AI Token 趋势图仅保留分析页**：工作台删除该图后右列由登录趋势单图撑满，不再并排双图。
4. **跳转语义保留**：今日概况卡仍可点击跳转对应管理页（今日 AI 相关两卡跳分析页），沿用工作台既有交互。
5. **locales**：工作台 stat 键改为今日语义（todayLoginSuccess/todayLoginFail/todayOper/online/todayAiCalls/todayAiTokens），分析页恢复 moduleDistribution 文案键，中英同构。

## Risks

- 工作台移除累计卡后，累计规模只能从分析页看到——预期行为（职能分区本意），无数据丢失。
- 单图撑满后登录趋势图高度不变，布局为 grid 单列，无响应式风险。
