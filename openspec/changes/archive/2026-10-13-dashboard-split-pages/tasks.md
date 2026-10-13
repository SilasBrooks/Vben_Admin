# dashboard-split-pages 任务清单

## 1. 前端工作台（今日视角）

- [x] 1.1 `workspace/index.vue`：7 张累计统计卡改为 6 张今日概况卡（今日登录成功/今日登录失败/今日操作/当前在线/今日 AI 调用/今日 AI Token，保留点击跳转），移除 AI Token 趋势图及其 useEcharts 实例
- [x] 1.2 locales 中英同构：workspace.stat 键改今日语义，清理不再使用的键

## 2. 前端分析页（全局洞察）

- [x] 2.1 `analytics/index.vue`：恢复操作模块分布图（消费 `moduleDistribution`，环形图），与部门/模型分布组成三图网格，AI Token 趋势保留为全站唯一 AI 趋势图
- [x] 2.2 locales 恢复 moduleDistribution 文案键（中英）

## 3. 验证与收尾

- [x] 3.1 `pnpm check:type` 通过
- [x] 3.2 浏览器验证：工作台仅今日卡+登录趋势单图，分析页三分布图+AI 趋势，两页无同类图表重复
- [x] 3.3 README 仪表盘功能行同步
- [x] 3.4 `openspec validate dashboard-split-pages --strict` 通过 → `openspec archive dashboard-split-pages --yes`（归档前不 commit）
