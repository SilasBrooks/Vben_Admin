# 技术设计：add-llm-model-config

## Context

AI 助手链路（已归档 add-ai-assistant / add-ai-context-memory / add-ai-menu-auth）中，唯一与厂商绑定的是 `DeepSeekClient`：JDK HttpClient 手写 SSE 解析 + Function Call 编排，端点参数（baseUrl/apiKey/model/timeout）来自 `@ConfigurationProperties(prefix = "deepseek")` 的 AiProperties。协议本身是 OpenAI 兼容格式，DeepSeek 只是取值之一——去厂商化的改造成本集中在「端点来源」而非「协议实现」。

## Goals / Non-Goals

**Goals:**

- 管理员可在 UI 自定义添加任意 OpenAI 兼容协议模型（DeepSeek/Qwen/Kimi/GLM/Ollama/one-api 等），全局唯一激活后对话与摘要即时切换，无需重启
- Key 安全底线：不进 git、接口不明文回显、编辑留空不覆盖
- 未配置激活模型时完全兼容旧行为（yml 兜底）

**Non-Goals:**

- 不做 Anthropic/Gemini 等非 OpenAI 兼容协议适配
- 不做 Key 加密存储（当前部署形态单库可控，加密收益低于复杂度成本；生产库靠访问控制）
- 不做多模型并存路由 / 按用户或按场景选模型，只做全局唯一激活
- 不做密钥轮换审计、用量统计计费

## Decisions

### 1. 端点抽象：LlmEndpoint + ActiveLlmResolver

- `LlmEndpoint` record `(baseUrl, apiKey, model, timeoutSeconds, temperature, maxTokens)` 是 LlmClient 的唯一入参来源，与配置存储解耦
- `ActiveLlmResolver.resolve()`：查 `is_active=1 AND enabled=1`，命中转 LlmEndpoint；否则回退 yml 兜底。**每次对话开始解析一次、整次对话固定**——低频查询不缓存，配置变更天然即时生效，且避免对话中途切换端点导致的上下文错乱

### 2. 协议改造而非重写：DeepSeekClient → LlmClient

- SSE 流式解析、Function Call 编排原样保留，仅把端点来源从 AiProperties 注入改为 resolver.resolve()
- `ChatRequest` 增加 `temperature`/`maxTokens`（`@JsonInclude(NON_EMPTY)`：null 时不出现在 JSON，保持厂商默认值）
- 新增 `ping(LlmEndpoint)`：非流式 `stream=false` + `max_tokens=1`，返回 PingResult(model, elapsedMs, reply)——连通测试不产生长回复费用，且能验证模型名正确性
- 错误文案去品牌化：401 → "API Key 无效或已过期，请在模型配置中检查该配置的 Key"；未配置 → 引导到「系统管理-模型配置」

### 3. 激活语义：表内 is_active 全局唯一

- `activate(id)` 事务内两步：全表 `is_active=0` → 目标行置 1（不依赖部分索引，PostgreSQL/MySQL 双方言通用）
- 停用（enabled=0）配置不可激活；激活中配置不可删除（先解除激活或删除前自动换绑由用户显式操作）——防止"删了正在用的配置导致 AI 全线不可用"
- 编辑时 apiKey 传空 = 不修改（`StringUtils.hasText` 判断），否则脱敏回显的 `sk-in****3456` 会被当成真实 Key 写回

### 4. 回显脱敏：Service 层 maskKey

- 长度 > 10 保留前 5 后 4，否则全 `*`；列表与详情统一走 `Item` record 出参，实体不出 Controller
- 明文入库的取舍：本系统管理面已有按钮级 RBAC + 操作日志，入库加密需要维护密钥分发，暂以"库访问控制 + 回显脱敏 + 审计"兜底（设计时已评估，生产部署 Checklist 同步提示）

### 5. 前端页面形态

- vxe-table 列表（状态/激活用 tag，激活中行操作列禁用删除）+ ElDialog 弹窗表单（Key 用 password 输入框，编辑时 placeholder 提示"留空则不修改"）
- 连通测试结果用 ElMessage 展示耗时与模型回复摘要；激活需 ElMessageBox 二次确认（影响全局）
- 按钮权限 `v-access:code` 与后端 `@RequirePermission` 一一对应（`System:Llm:{List,Add,Edit,Delete,Activate}`）

## Risks / Trade-offs

- **Key 明文入库**：见 Decision 4，已知取舍；若未来多租户化必须升级为加密存储
- **激活坏配置导致 AI 不可用**：属显式操作后果，连通测试提供上线前验证；错误信息可读指回模型配置页，无静默降级（fail-fast，不偷偷回退 yml——避免"以为在用新模型实际在用兜底"的隐性错配）
- **每对话一次 resolve 查询**：低频（对话开始一次），不加缓存；若未来高频化可加 30s 本地缓存 + 变更失效
- **存量库**：种子 seeder 仅空库执行，已有库走 `upgrade-20260921-llm-config.sql` 幂等脚本（NOT EXISTS 保护），两处菜单数据保持一致
