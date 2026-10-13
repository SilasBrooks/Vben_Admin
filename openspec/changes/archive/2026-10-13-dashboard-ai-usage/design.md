# dashboard-ai-usage 设计

## Context

- 工作台/分析页已接真实 `GET /dashboard/summary`（spec `platform/dashboard-stats`），图表用 `@vben/plugins/echarts` 封装，但只有系统运营维度（用户/角色/登录/操作）。
- Token 数据完全未采集：`LlmClient.parseSse`（service/src/main/java/com/vben/service/module/ai/client/LlmClient.java L162-239）只解析 content 与 tool_calls；`choice.isMissingNode() → return`（L181-183）恰好把 OpenAI 协议中 choices 为空数组的 usage 末尾分片直接跳过；请求体 `ChatRequest`（L49-52）无 stream_options 字段。
- `streamChat` 调用点仅两处：`AiChatService.chat`（L115，AI 助手对话）与 `AiChatService.summarize`（L289，历史压缩），均为匿名 `StreamHandler` 实现类。
- `AiChatService` 无任何消息持久化（AI 对话历史存前端本地），token 统计必须自建落库表。
- dashboard summary 是登录即可见的聚合接口（无权限码），工作台/分析页对全部角色开放；token 聚合数据为非敏感统计，与该语义一致。

## Goals / Non-Goals

**Goals**

- 每次业务 LLM 调用（chat / summarize）的 usage 落库，可统计可展示
- `GET /dashboard/summary` 提供 `aiUsage` 聚合数据块
- 工作台首页有显眼的 token 用量提示（今日 AI Token 统计卡 + 趋势图）
- 分析页提供 AI 用量趋势与模型分布维度
- 工作台增加快捷入口导航（按当前用户权限显隐）

**Non-Goals**

- 失败调用与上游错误统计落库（后续如需错误率分析，可给 `sys_llm_call_log` 加 success 列扩展）
- 按用户维度的用量权限隔离（summary 本就是全站聚合语义；个人维度用量属个人中心范畴，另行变更）
- ping（模型配置页连通性测试，max_tokens=1）采集——非业务调用，会污染统计
- Embedding / Rerank 调用用量——仅覆盖 LLM chat/completions
- 成本/价格换算——仅 tokens 原始计数

## Decisions

1. **采集回调在 StreamHandler，落库不在 LlmClient**：`StreamHandler` 新增 `default void onUsage(LlmUsage usage) {}`（default 空实现，既有匿名类不强制实现、不破坏编译）；`LlmClient` 保持纯 HTTP 客户端职责——它不知道 scene 与业务上下文，落库由调用方 `AiChatService` 接线。
2. **回调携带模型名与单请求耗时**：多轮工具调用 = 多次上游请求，每次 `streamChat` 产生一次回调、落一条记录，真实反映每次请求的 token 与耗时；模型名由 `LlmClient` 从 `endpoint.model()` 带出（多轮对话整轮固定同一模型）。
3. **stream_options 恒携带**：`stream_options: {"include_usage": true}` 是 OpenAI 兼容协议标准字段，主流实现（OpenAI/DeepSeek/GLM/方舟/dashscope）均支持；不做配置开关，视为协议组成部分。
4. **usage 缺失语义**：上游未返回 usage（网关剥离或不支持）时仍落库，tokens 三列记 NULL——「调用次数」始终准确，token 数可能低估；统计 SUM 忽略 NULL、COUNT 不忽略，前端对 NULL 以「-」展示。接受该保守近似，不虚报。
5. **scene 用 varchar + 应用层常量**：`chat` / `summarize` 两个值，不建字典表、不做枚举表。
6. **落库同步执行 + 失败静默**：流结束回调处同步单条 insert（本地 PG 微秒级）；insert 异常仅 log.warn，不影响对话结果返回。
7. **aiUsage 挂既有 summary，不新增接口**：前端一次请求取全数据，与既有聚合语义、权限语义一致；按天聚合走 MyBatis-Plus 自定义 SQL（group by 日期）。
8. **页面沿用既有技术栈**：Page + EchartsUI + tailwind grid 布局，不引入新图表库；快捷入口用 `v-access:code` 复用既有权限码控制显隐（入口清单实现时从既有权限码中选取常用页面）。

## Risks / Trade-offs

- **stream_options 兼容性（中概率、低影响）**：个别旧网关可能不识别该字段返回 400。缓解：视为协议标准字段；错误经 mapHttpError 有可读文案，用户可感知可修正；如实际遇到再评估加配置开关。
- **token 数低估（低影响）**：usage 缺失记 NULL，统计偏保守不虚报，调用次数不受影响。
- **多轮工具调用记录条数多（低影响）**：一次对话 1~N 条记录，属真实上游请求数；数据量为调用频次级别，按天聚合无压力。
- **升级 SQL 幂等**：`CREATE TABLE IF NOT EXISTS`，可重复执行；无菜单/权限码变更，Seeder 不动。
