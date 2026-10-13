# ai/llm-usage 变更 delta

## ADDED Requirements

### Requirement: LLM 调用用量采集

系统 SHALL 在每次业务 LLM 调用完成后，将该次上游请求的用量落库到 `sys_llm_call_log`：scene（`chat`=助手对话 / `summarize`=历史压缩）、model、prompt_tokens、completion_tokens、total_tokens、elapsed_ms（该请求耗时）、create_time。

- 流式请求 SHALL 携带 `stream_options: {"include_usage": true}`，并解析响应末尾的 usage 分片（该分片 choices 为空数组，MUST NOT 被提前跳过）
- 多轮工具调用的对话 SHALL 按实际发生的每次上游请求各记一条记录
- 上游未返回 usage 时 SHALL 仍落库记录，tokens 三列记 NULL（调用次数保持准确）
- 模型配置页连通性测试（ping）SHALL NOT 记录（非业务调用）
- 落库失败 MUST NOT 影响对话结果返回（仅记录告警日志）

#### Scenario: 对话完成后用量落库

- **WHEN** 用户在 AI 助手完成一轮无工具调用的对话
- **THEN** `sys_llm_call_log` 新增一条 scene=chat 记录，model 为当次激活模型，tokens 与上游 usage 一致，elapsed_ms 为该请求耗时

#### Scenario: 多轮工具调用逐请求记账

- **WHEN** 一轮对话触发 N 次上游请求（对话 → 工具调用 → … → 最终回答）
- **THEN** `sys_llm_call_log` 新增 N 条 scene=chat 记录，各条 tokens 为对应请求的 usage

#### Scenario: 上游未返回 usage

- **WHEN** 激活的模型服务未在流式响应中返回 usage 分片
- **THEN** 记录仍落库且调用次数可统计，tokens 三列为 NULL，对话流程不受影响

#### Scenario: 历史压缩计入用量

- **WHEN** 对话历史压缩（summarize）触发 LLM 调用
- **THEN** 落库记录 scene=summarize，tokens 与该请求 usage 一致

### Requirement: 仪表盘 AI 用量数据块

`GET /api/dashboard/summary` SHALL 在响应中新增 `aiUsage` 数据块（登录即可见，与既有聚合统计权限语义一致）：累计/今日/本月的 token 总量及调用次数、近 14 天每日 token 合计与调用次数序列（日期升序）、近 30 天按模型聚合的 token Top5（模型名 + tokens + 次数）；无任何记录时数值为 0、序列为空数组。

#### Scenario: 登录用户查看 AI 用量统计

- **WHEN** 任意已登录用户请求 `GET /api/dashboard/summary`
- **THEN** `aiUsage` 各字段与 `sys_llm_call_log` 聚合结果一致（tokens 为 NULL 的记录计入次数、不计入 token 合计）
- **AND** 未认证请求仍返回 401（与既有接口行为一致）
