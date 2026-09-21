# 任务清单：add-llm-model-config

## 1. 数据库

- [x] 1.1 `sys_llm_config` 建表 DDL 追加到 `schema-postgres.sql` 与 `schema-mysql.sql`（id/name/base_url/api_key/model/temperature/max_tokens/timeout_seconds/enabled/is_active/remark/时间戳）
- [x] 1.2 存量库幂等升级脚本 `db/upgrade-20260921-llm-config.sql`（建表 + 模型配置菜单 + System:Llm:* 权限码 + super/admin 角色授权，NOT EXISTS 保护），在 vben5 容器执行成功

## 2. 后端：模型配置管理

- [x] 2.1 实体 `SysLlmConfig`（@TableName + 时间自动填充）与 `SysLlmConfigMapper`（置于 `llm/mapper/` 子包对齐 @MapperScan）
- [x] 2.2 `SysLlmConfigService`：CRUD + activate（事务内全表清零再置目标行）+ ping 透传 + maskKey 脱敏；校验：名称唯一、激活中禁删、停用禁激活、温度 [0,2]、maxTokens 上限、编辑空 Key 不覆盖
- [x] 2.3 `LlmConfigController`（/system/llm）：list/add/update/delete/activate/test 六接口，权限码 System:Llm:{List,Add,Edit,Delete,Activate}，写接口 @Idempotent/@RateLimit，删除与激活 @OperLog
- [x] 2.4 `error.ai.llm.*` 8 条错误消息中英双语（messages.properties / messages_en_US.properties）

## 3. 后端：LLM 客户端可插拔

- [x] 3.1 `LlmEndpoint` record + `ActiveLlmResolver`（激活配置优先、yml DeepSeek 兜底，每次对话解析一次）
- [x] 3.2 `DeepSeekClient` 重构为 `LlmClient`：streamChat 端点改由 resolver 提供，ChatRequest 增加 temperature/maxTokens（NON_EMPTY），新增非流式 ping；错误文案去品牌化
- [x] 3.3 `AiChatService` 对话与摘要两处调用切换到 LlmClient，删除 DeepSeekClient
- [x] 3.4 单测 `LlmConfigServiceTest` 11 用例（maskKey 边界、名称重复、空 Key 不覆盖、激活互斥/停用拒绝、激活中禁删、ping 透传）全过

## 4. 菜单与 AI 工具同步

- [x] 4.1 `DatabaseSeeder` 追加 SystemLlm 目录/菜单（order 8）+ 5 个 F 权限码
- [x] 4.2 `AiSystemTools.MENU_TITLE_ZH` 登记 `page.system.llm → 模型配置`

## 5. 前端

- [x] 5.1 `api/system/llm.ts`：LlmItem（脱敏 Key）/LlmSaveParams 类型 + 6 个 API 函数
- [x] 5.2 `views/system/llm/index.vue`：vxe-table 列表 + 搜索 + 新增/编辑弹窗（Key password、编辑留空提示）+ 测试连通 + 激活确认 + 删除保护 + v-access:code 按钮权限
- [x] 5.3 语言包 `locales/langs/{zh-CN,en-US}/llm.json` 同构 + `page.json` 菜单标题（模型配置/Model Config）；`pnpm check:type` 通过

## 6. 验证与收尾

- [x] 6.1 curl E2E 全绿：脱敏回显、名称唯一、激活互斥、连通测试 401/连接失败两路径、AI 链路走激活配置（坏地址返回"暂时不可用"而非"未配置 Key"）、激活中禁删、清理数据
- [x] 6.2 README 同步：技术栈、功能清单、快速开始、安全红线、部署 Checklist 共 7 处（DEEPSEEK_API_KEY 降级为兜底说明）
- [x] 6.3 归档 openspec 变更（sync delta → archive）
