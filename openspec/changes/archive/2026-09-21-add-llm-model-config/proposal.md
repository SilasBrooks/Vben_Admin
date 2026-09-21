# 变更提案：add-llm-model-config

## Why

AI 助手的底层大模型硬编码为 DeepSeek（yml `deepseek.*` + 环境变量 `DEEPSEEK_API_KEY`）：换模型要改配置重启发版，无法在运行时切换；实测（2026-09-20）DeepSeek 官方 Key 失效后只能改环境变量重启。管理员需要在不重启、不发版的前提下自定义添加任意 OpenAI 兼容协议的模型并即时切换，以解除对单一厂商的绑定。

## What Changes

- 新增「模型配置」管理模块（系统管理 → 模型配置，order 8）：
  - `sys_llm_config` 表：名称、接口地址 baseUrl、API Key（明文入库、接口回显脱敏 `sk-in****3456` 格式）、模型名、温度、maxTokens、超时秒数、enabled、is_active
  - 6 个管理接口（`/system/llm`）：分页列表 / 新增 / 编辑 / 删除 / 激活 / 连通测试，权限码 `System:Llm:{List,Add,Edit,Delete,Activate}`，写接口挂幂等与限流
  - 激活语义：全局唯一（事务内全表清零 + 置目标行）；激活中配置禁删；停用配置不可激活；编辑时 Key 留空 = 不修改
  - 连通测试：非流式 ping（`stream=false` + `max_tokens=1`）返回模型回复与耗时，用于上线前验证 Key 与地址
- LLM 客户端去品牌化：`DeepSeekClient` 重构为通用 `LlmClient`（OpenAI 兼容协议 SSE 流式 + Function Call），每次对话开始经 `ActiveLlmResolver` 解析端点——命中激活配置用 DB 配置，否则回退 yml DeepSeek 兜底；配置变更即时生效，无需重启
- AI 对话与上下文摘要统一走 `LlmClient`（摘要提供方随之跟随激活配置）
- 前端新增模型配置页（vxe-table + 弹窗表单 + 连通测试 + 激活确认），中英文案同构
- 错误文案去品牌化并双语化（`error.ai.llm.*` 8 条）

## Capabilities

### New Capabilities

- `system/llm-config`：LLM 模型配置管理（CRUD/激活/连通测试/脱敏回显/删除保护）

### Modified Capabilities

- `ai/assistant`：底层大模型从 DeepSeek 硬编码改为「激活配置优先 + yml 兜底」可插拔解析；未配置场景与错误提示同步更新；摘要提供方跟随激活配置

## Impact

- **后端**：`module/ai/llm/`（实体/Mapper/Service/Controller/LlmEndpoint/ActiveLlmResolver）；`client/LlmClient` 替代 `DeepSeekClient`（删除旧类）；`AiChatService` 两处调用点切换；`DatabaseSeeder` 菜单与权限码；`AiSystemTools.MENU_TITLE_ZH` 登记 `page.system.llm`
- **前端**：`api/system/llm.ts` + `views/system/llm/index.vue` + `locales/langs/{zh-CN,en-US}/llm.json` + `page.json` 菜单标题
- **数据库**：`sys_llm_config` 建表（schema-postgres.sql / schema-mysql.sql）；存量库幂等升级脚本 `db/upgrade-20260921-llm-config.sql`（建表 + 菜单 + 角色授权，已在 vben5 容器执行）
- **安全**：API Key 明文入库、接口回显脱敏、编辑留空不修改；`System:Llm:*` 权限码前后端一一对应；写接口幂等防重、连通测试限流 6 次/分钟
- **兼容**：未配置激活模型时行为与旧版一致（yml `DEEPSEEK_API_KEY` 兜底），零迁移成本
