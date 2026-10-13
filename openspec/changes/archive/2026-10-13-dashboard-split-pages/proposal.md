# dashboard-split-pages

## Why

dashboard-ai-usage 落地后，工作台与分析页内容高度相似：AI Token 趋势图两页完全重复，统计卡与概览卡的累计规模指标（用户/角色）重叠，两页缺乏职能分工，用户反馈「内容相似度太多」。

## What Changes

- 工作台重新定位为「今日视角 + 操作入口」：7 张累计规模统计卡改为 6 张今日概况卡（今日登录成功/失败、今日操作、当前在线、今日 AI 调用、今日 AI Token），移除 AI Token 趋势图，保留欢迎横幅、快捷入口、登录趋势图、最近登录
- 分析页重新定位为「全局洞察」：保留 6 张概览卡与 AI Token 趋势图（全站唯一 AI 趋势图），分布图扩为三张（部门人数 + 模型 Token + 操作模块 Top5，恢复被移除的模块分布图），保留最近操作
- 两页不再出现同类图表与同类指标卡，纯前端改动，`GET /dashboard/summary` 协议不变（today/moduleDistribution 数据块已存在且已返回）

## Capabilities

- MODIFIED platform/dashboard-stats：工作台页需求（今日概况卡 + 移除 AI 趋势图）、分析页需求（三分布图）

## Impact

- 前端：`views/dashboard/workspace/index.vue`、`views/dashboard/analytics/index.vue`、`locales/langs/{zh-CN,en-US}/dashboard.json`
- 后端无改动；README 仪表盘功能行描述同步
