# dashboard-permission-filter 设计

## Context

监控页（操作日志/登录日志）已有权限码 `Monitor:OperLog:List` / `Monitor:LoginLog:List`，但仪表盘聚合接口未接入权限判断，任意登录用户可读到全系统操作明细、他人登录 IP 等监控数据。LoginUser（LoginUserHolder）持有 permissions 集合，前端 accessStore.accessCodes 同步持有同一份权限码，两侧过滤条件可对齐。

## Goals

- 监控类数据块服务端按权限过滤（数据不出网，非仅前端隐藏）
- 前端无权限时不渲染对应卡片/图表/列表（而非显示 0——0 会被误读为"没有发生"）
- 权限变更后刷新页面即生效（权限码走既有鉴权快照缓存与失效机制）

## NonGoals

- 不做数据权限（行级）粒度的仪表盘过滤，只做权限码级「可见/不可见」
- 不改动监控页本身与权限码体系，不新增菜单
- 不处理 AI 用量、部门分布等非监控数据的细粒度隔离（保持全员可见）

## Decisions

1. **权限判断位置**：DashboardService 装配时读 `LoginUserHolder.require().getPermissions()`，缺失权限的数据块直接装配为 null，敏感数据不产生序列化输出（服务端过滤优先于前端隐藏）。
2. **权限码归属**：`Monitor:OperLog:List` → recentOpers、moduleDistribution、today.operCount、totals.operCount；`Monitor:LoginLog:List` → recentLogins、loginTrend、today.loginSuccess、today.loginFail、totals.loginCount。明细与衍生统计同码，避免"看得到图看不到表"的割裂。
3. **null 而非 0**：无权限字段返回 null（空值语义 = 不可见），有权限无数据返回 0/空数组（语义 = 没有发生）；前端 TS 类型改为可空。
4. **前端显隐方式**：卡片列表在 computed 中按 `value === null` 过滤（不用 v-access 逐卡包裹）；图表/列表区块用 `v-if="summary?.xxx !== null"`（数据驱动，与后端过滤天然一致，避免前端权限码与后端判断不一致的边缘 case）。
5. **全员可见范围**：totals 的用户/角色/部门/在线/文件/字典计数、deptDistribution、aiUsage 不受影响——规模数字无个体隐私，AI 用量不含用户维度。
6. **单测口径**：mock LoginUserHolder（set/clear），分别断言 super（全可见）与无监控权限（受控字段为 null、aiUsage 等不受影响）两种装配结果。

## Risks

- 存量前端类型把受控字段当必有值使用（workspace/analytics 已在同变更内改为可空并处理，无其他消费方）。
- 管理员看到的面板内容比普通用户多属预期行为；角色授权调整后需刷新页面重新拉取权限码。
