# dashboard-ai-usage 任务清单

## 1. 数据与 SQL（两处一致）

- [x] 1.1 `schema-postgres.sql` 新增 `sys_llm_call_log`（scene varchar、model varchar、prompt_tokens/completion_tokens/total_tokens bigint 可空、elapsed_ms bigint、create_time，create_time 索引）
- [x] 1.2 存量库升级 SQL `upgrade-20261013-llm-usage-postgres.sql`（幂等建表；无菜单/权限码变更）

## 2. 后端用量采集（module/ai）

- [x] 2.1 实体 `SysLlmCallLog` + mapper（放 `module/ai/usage/mapper` 子包）+ `LlmUsageService.record`（同步 insert，异常 log.warn 静默不影响业务）
- [x] 2.2 `LlmClient`：`ChatRequest` 增加 `stream_options` 字段（`{"include_usage": true}`）；`parseSse` 识别 usage 分片（choices 为空数组但 usage 存在时不再提前 return）；`StreamHandler` 新增 `default void onUsage(LlmUsage usage)`（LlmUsage 含 model/promptTokens/completionTokens/totalTokens/elapsedMs，由 LlmClient 计算单请求耗时）
- [x] 2.3 `AiChatService.chat` 与 `summarize` 两处匿名 StreamHandler 实现 onUsage → `LlmUsageService` 落库（scene=chat / scene=summarize）
- [x] 2.4 单测：usage 分片解析（choices 空数组 + usage 分片可触发回调、无 usage 流不触发、多 chunk 聚合不回归）、`record` 静默失败

## 3. 后端统计扩展（module/dashboard）

- [x] 3.1 `DashboardService.summary` 新增 `aiUsage`：totalTokens/totalCalls（累计）/ todayTokens / todayCalls / monthTokens / monthCalls / tokenTrend（近 14 天每日 tokens 合计 + 调用次数，日期升序）/ modelDistribution（近 30 天按模型 tokens Top5，含次数）
- [x] 3.2 单测：聚合口径（当日边界、tokens NULL 计入次数不计入合计、空表返回 0 与空数组、Top5 截断）

## 4. 前端（apps/web-ele）

- [x] 4.1 `api/dashboard.ts`：`DashboardSummary` 增加 `AiUsage` 类型与字段
- [x] 4.2 工作台重写：7 张统计卡（+今日 AI Token，点击跳分析页）、登录趋势与 AI Token 趋势并排两图、快捷入口导航（`v-access:code` 按权限显隐）、最近登录保留
- [x] 4.3 分析页重写：6 张概览卡（+累计 AI 调用/累计 AI Token）、AI Token 用量趋势图（每日 token 柱 + 调用次数线）、模型用量分布环形图（与部门分布并排）、最近操作保留
- [x] 4.4 locales 中英同构（`page.dashboard` 命名空间新增文案，NULL token 展示「-」）
- [x] 4.5 `pnpm check:type` 通过

## 5. 验证与收尾

- [x] 5.1 后端 `mvn test` 全绿
- [x] 5.2 E2E：本地 mock LLM（SSE + usage 分片，不调用 Doubao-embedding-vision）→ AI 对话两轮 → summary `aiUsage` 数值正确 → 工作台/分析页渲染与数值正确 → 空态场景（无记录时 0 与空态图）
- [x] 5.3 README「项目亮点 / 功能清单 / 注意事项」同步
- [x] 5.4 `openspec validate dashboard-ai-usage --strict` 通过 → `openspec archive dashboard-ai-usage --yes`（归档前不 commit）
