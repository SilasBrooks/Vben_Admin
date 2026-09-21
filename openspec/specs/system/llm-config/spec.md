# system/llm-config Specification

## Purpose
TBD - created by archiving change add-llm-model-config. Update Purpose after archive.

## Requirements

### Requirement: 模型配置管理

系统 SHALL 提供模型配置管理接口（`/system/llm`）：分页查询（System:Llm:List）、新增（System:Llm:Add，幂等 + 限流）、编辑（System:Llm:Edit）、删除（System:Llm:Delete，审计）、激活（System:Llm:Activate，幂等 + 审计）。每条配置 SHALL 包含名称、接口地址 baseUrl、API Key、模型名、温度、maxTokens、超时秒数与启停状态；配置名 MUST 全局唯一；温度 MUST 在 [0,2] 区间。协议范围限定为 OpenAI 兼容协议（`/chat/completions`）。

#### Scenario: 名称重复拒绝
- **WHEN** 新增配置时提交已存在的名称
- **THEN** 返回 400 业务错误「模型名称「X」已存在」，不创建

#### Scenario: 编辑留空不覆盖 Key
- **WHEN** 编辑配置时 API Key 输入框留空提交
- **THEN** 库中原 Key 保持不变，接口回显仍为原脱敏值

### Requirement: API Key 回显脱敏

配置列表与编辑回显 MUST 对 API Key 脱敏（长度 > 10 保留前 5 后 4，如 `sk-in****3456`，否则全 `*`），任何接口响应 MUST NOT 返回 Key 明文；Key 仅在服务端调用大模型时使用。

#### Scenario: 列表回显脱敏
- **WHEN** 具备 System:Llm:List 权限的用户查看配置列表
- **THEN** apiKey 列显示脱敏值（如 `sk-in****3456`），无明文

### Requirement: 全局唯一激活

系统 SHALL 提供激活接口：全局同时 MUST 仅存在一条激活配置（事务内全表清零后置目标行）。停用（enabled=0）的配置 MUST 拒绝激活；激活中的配置 MUST 拒绝删除。激活操作 MUST 无需重启即时生效。

#### Scenario: 激活互斥
- **WHEN** 先激活配置 A 再激活配置 B
- **THEN** 仅 B 处于激活状态，A 自动解除

#### Scenario: 停用配置不可激活
- **WHEN** 对 enabled=0 的配置调用激活
- **THEN** 返回业务错误，激活状态不变

#### Scenario: 激活中配置禁删
- **WHEN** 删除当前激活的配置
- **THEN** 返回业务错误「该模型正在使用中，请先激活其他模型」，配置保留

### Requirement: 连通测试

系统 SHALL 提供按配置 id 的连通测试接口（System:Llm:Edit，限流）：以非流式方式（`stream=false`、`max_tokens=1`）向该配置的接口地址发起真实请求，返回模型回复摘要与耗时；测试使用库中真实 Key。Key 无效时 MUST 返回可读中文错误；地址不可达时 MUST 返回连接失败类错误。

#### Scenario: Key 无效
- **WHEN** 对配置执行连通测试且该配置 Key 已失效
- **THEN** 返回「AI 服务 API Key 无效或已过期」类可读错误，含上游状态信息

#### Scenario: 地址不可达
- **WHEN** 对接口地址错误（无法建立连接）的配置执行连通测试
- **THEN** 返回「无法连接到接口地址」类错误，不暴露堆栈

### Requirement: 前端模型配置页面

前端 SHALL 在「系统管理」下提供模型配置页面：列表（名称/模型/接口地址/脱敏 Key/状态/激活状态/创建时间）+ 新增/编辑弹窗（Key 用密码输入框，编辑时提示"留空则不修改"）+ 连通测试 + 激活（二次确认）+ 删除（激活中禁用），操作按钮分别以 `System:Llm:Add/Edit/Delete/Activate` 权限码控制。

#### Scenario: 无权限按钮隐藏
- **WHEN** 无 System:Llm:Activate 权限的账号进入模型配置页
- **THEN** 不渲染激活按钮

#### Scenario: 激活需确认
- **WHEN** 用户点击激活某配置
- **THEN** 出现二次确认弹框，确认后列表中该配置标记激活、其余配置自动解除
