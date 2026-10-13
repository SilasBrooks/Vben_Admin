# system/rerank-config Specification

## Purpose
TBD - created by archiving change kb-rerank-citation-jump. Update Purpose after archive.

## Requirements

### Requirement: Rerank 模型配置管理

系统 SHALL 提供 Rerank 精排模型配置的增删改查（系统管理-精排配置，`/system/rerank`）：字段含显示名（唯一）、接口根地址（不带末尾斜杠）、API Key、模型名、单次上游请求超时（秒，默认 30）、启用状态与备注。安全模式 MUST 与嵌入配置一致：API Key 明文入库（内网约定）、列表与详情回显前 MUST 脱敏、编辑时 Key 传空 MUST 保持原值不覆盖。激活状态 `is_active` 全局唯一（激活切换由事务保证，停用状态的配置 MUST 不可激活）。权限码：`System:Rerank:List`（列表）、`System:Rerank:Add`（新增）、`System:Rerank:Edit`（编辑/连通测试）、`System:Rerank:Delete`（删除）、`System:Rerank:Activate`（激活切换）；写操作 MUST 限流与幂等防护，删除与激活 MUST 记审计日志。当前激活中的配置 MUST 不可删除（需先切换其他配置）。Rerank 配置 MUST 无 yml 兜底（未配置即精排层关闭，检索行为退化为纯 RRF 融合，不报错）。

#### Scenario: 新增并激活精排配置

- **WHEN** 管理员新增精排配置（如硅基流动 BGE-reranker-v2-m3）并点击激活
- **THEN** 该配置 `is_active=1` 且其余配置自动置 0，此后知识库检索即时走精排层，无需重启

#### Scenario: Key 脱敏回显与留空不改

- **WHEN** 管理员打开已配置 Key 的精排配置编辑框且不修改 Key 提交
- **THEN** 列表/详情回显的 Key 为脱敏形态，提交后原 Key 保持不变

#### Scenario: 激活中配置禁删

- **WHEN** 管理员删除当前激活的精排配置
- **THEN** 返回可读业务错误，需先激活其他配置或明确关闭精排后再删除

### Requirement: 精排连通测试

系统 SHALL 提供精排配置连通测试接口（System:Rerunank:Edit 权限，限流）：用该配置真实调用一次 rerank 接口（样例 query 与 2-3 条样例文档），返回耗时与 Top-1 相关性得分供管理员验证接通性；测试失败 MUST 返回可读错误（含上游状态码/原因摘要）。测试 MUST NOT 修改任何业务数据。

#### Scenario: 连通测试成功

- **WHEN** 管理员对正确填写的精排配置点击「连通测试」
- **THEN** 返回上游耗时与样例 Top-1 相关性得分，提示测试通过

#### Scenario: 连通测试失败

- **WHEN** 精排配置的 API Key 错误时点击「连通测试」
- **THEN** 返回可读错误（含上游拒绝原因摘要），不改变配置状态

### Requirement: 前端精排配置页面

前端 SHALL 在「系统管理」下提供精排配置页面：配置列表（激活配置置顶标识）+ 新增/编辑弹窗 + 激活切换（二次确认）+ 连通测试 + 删除（二次确认），按钮以 `System:Rerank:*` 权限码控制；页面结构与交互 MUST 与嵌入配置页保持一致。

#### Scenario: 精排配置页可用

- **WHEN** 拥有 System:Rerank:List 权限的管理员进入精排配置页
- **THEN** 可见配置列表并可按权限执行新增/编辑/激活/测试/删除操作
