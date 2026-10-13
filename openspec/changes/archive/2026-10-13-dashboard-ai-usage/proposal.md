# dashboard-ai-usage

## Why

仪表盘两页（工作台/分析页）目前只呈现系统运营统计（用户/角色/登录/操作），而项目最有价值的 AI 能力（助手对话、历史压缩）完全没有量化呈现——token 消耗了多少、调用了多少次、哪个模型在消耗，全链路无任何记录（`LlmClient` 的 SSE 解析直接丢弃上游返回的 `usage` 字段，甚至把 choices 为空的 usage 末尾分片提前 return 跳过）。同时两页信息维度单薄，缺少 AI 视角与快捷入口。本次变更让 LLM 调用用量可观测、在首页工作台有直观的 token 用量提示，并把工作台/分析页完善为「系统运营 + AI 用量」双视角。

## What Changes

1. **LLM 用量采集**：`LlmClient` 流式请求携带 `stream_options.include_usage`，SSE 解析末尾 usage 分片（choices 为空数组但 usage 存在，不再被提前跳过），通过 `StreamHandler` 新增的 default 回调 `onUsage` 带出（模型名 + prompt/completion/total tokens + 单请求耗时）；非流式 ping（连通性测试）不采集。
2. **用量落库**：新表 `sys_llm_call_log`（scene/model/tokens/elapsed_ms/create_time），`AiChatService` 对话（chat）与历史压缩（summarize）两处调用点接线落库；多轮工具调用按实际发生的每次上游请求各记一条；上游未返回 usage 时 tokens 记 NULL（调用次数仍准确）；仅记录成功调用。
3. **仪表盘统计扩展**：`GET /dashboard/summary` 新增 `aiUsage` 数据块——今日/本月 token 总量与调用次数、近 14 天每日 token 与调用次数趋势、近 30 天按模型聚合 token Top5；登录即可见（与既有 summary 聚合统计权限语义一致）。
4. **工作台重写**：欢迎横幅 + 7 张统计卡（新增「今日 AI Token」）+ 双趋势图（登录趋势 / AI Token 趋势并排）+ 快捷入口导航（按当前用户权限显隐）+ 最近登录列表。
5. **分析页重写**：6 张概览卡（新增累计 AI 调用 / 累计 AI Token）+ AI Token 用量趋势图（每日 token 柱 + 调用次数线）+ 模型用量分布环形图（与部门分布并排）+ 最近操作列表。
6. 存量库升级 SQL（仅建表，无菜单/权限码变更——工作台/分析页与 summary 均为既有登录可见面）。

## Capabilities

### New Capabilities

- `ai/llm-usage`：LLM 调用用量采集与落库——采集语义（stream_options + usage 分片解析 + 回调）、usage 缺失语义、scene 枚举（chat/summarize）、ping 不采集、落库失败静默。

### Modified Capabilities

- `platform/dashboard-stats`：MODIFIED 三条需求——「仪表盘聚合接口」（数据块清单追加 `aiUsage`）、「工作台页面展示真实数据」（改写为含 AI 用量的双视角布局 + 快捷入口）、「分析页展示真实数据」（改写为含 AI 概览卡与用量图表）；「移除模板演示组件」需求不变。

## Impact

- **后端**：`module/ai/client/`（`LlmClient` ChatRequest 加 stream_options 字段、parseSse 识别 usage 分片、`StreamHandler` 加 default 方法）；`module/ai/agent/AiChatService` 两处匿名 StreamHandler 接线；新增 `module/ai/usage/`（实体 + mapper 子包 + `LlmUsageService`）；`module/dashboard/DashboardService` 聚合扩展；`schema-postgres.sql` 建表 + 存量升级 SQL（幂等）。
- **前端**：`api/dashboard.ts` 类型扩展；`views/dashboard/workspace/index.vue` 与 `views/dashboard/analytics/index.vue` 重写；locales 中英同构新增文案。
- **兼容性**：`/dashboard/summary` 为加法扩展（新增数据块，不动既有字段）；`StreamHandler` 新增 default 方法不破坏既有匿名实现；AI 对话行为与协议不变（仅请求体多一个标准协议字段 `stream_options`）。
