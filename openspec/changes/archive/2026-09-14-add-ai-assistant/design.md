# 技术设计：add-ai-assistant

## 1. 总体架构

```
浏览器 AiAssistant 组件
   │  POST /api/ai/chat（SSE）
   ▼
AiController ── AiChatService（Agent 编排循环）
                    │
                    ├── DeepSeekClient（JDK HttpClient，stream=true）
                    │       └── HTTPS → api.deepseek.com（Key 在后端）
                    │
                    └── AiToolExecutor
                            ├── 查询工具：直接执行，结果回喂
                            └── 新增工具：不下发执行，转确认卡片
   │  用户点「确认执行」
   ▼
POST /api/ai/tool/execute（JSON）→ AiToolExecutor → 现有 Admin Service
   │
   ▼（前端把 tool 结果加入消息，再次调 /ai/chat）
模型流式输出中文执行结果
```

后端无会话状态：完整 messages（OpenAI 协议形态）由前端持有与回传，后端每次请求无状态转发+编排。

## 2. 后端模块（`com.vben.service.module.ai`）

### 2.1 配置 `AiProperties`

`@ConfigurationProperties(prefix = "deepseek")`：

| 字段 | 默认值 | 说明 |
|---|---|---|
| base-url | https://api.deepseek.com | DeepSeek 接口根地址 |
| api-key | 空 | 从 `DEEPSEEK_API_KEY` 环境变量覆盖；为空时接口返回"AI 服务未配置" |
| model | deepseek-chat | 模型名，可配 |
| timeout-seconds | 60 | 上游超时 |
| max-tool-rounds | 3 | 单次请求内查询工具自动执行的最大轮数 |

`application-dev.yml` 增加配置块；本地联调的 Key 只写入 dev 配置（不进生产配置、不进前端）。

### 2.2 DeepSeekClient

- JDK 17+ 内置 `java.net.http.HttpClient`，零新增 Maven 依赖
- `streamChat(request, onDelta, onToolCall, onError)`：POST `{base-url}/v1/chat/completions`，`stream=true`
- 按 SSE 行解析 `data: {json}`：
  - `choices[0].delta.content` → 文本片段回调
  - `choices[0].delta.tool_calls[]` → 按 `index` 聚合 `id` / `function.name` / `arguments` 分片（DeepSeek 分片下发 JSON 字符串）
  - `finish_reason=tool_calls` → 输出聚合完成的工具调用列表
  - `[DONE]` → 正常结束
- 上游 401（Key 错）/429（限流）/超时 → 转换为中文错误信息

### 2.3 工具定义（DeepSeek tools，OpenAI JSON Schema 形态）

| 工具名 | 类型 | 参数 | 需要权限码 |
|---|---|---|---|
| query_users | 查询 | keyword?（用户名/昵称） | System:User:List |
| query_roles | 查询 | keyword?（角色名/标识） | System:Role:List |
| query_depts | 查询 | keyword?（部门名） | System:Dept:List |
| create_user | 新增 | username, password, nickname, deptName?, roleNames? | System:User:Add |
| create_role | 新增 | roleKey, roleName, dataScope?（1-5 中文映射） | System:Role:Add |
| create_dept | 新增 | deptName, parentName?, sortNum? | System:Dept:Add |

设计要点：
- 对模型暴露**名称**（deptName/parentName/roleNames），执行器内部解析为 id（部门按名查树、角色按 roleKey/roleName 匹配）；查不到时返回「未找到名为 X 的部门/角色」让模型追问，不猜测
- 工具 description 用中文写清语义与示例（DeepSeek 对中文工具描述理解稳定）
- 返回给模型的结果是**精简 JSON**（只含 id/名称/状态等必要字段，不返回密码等敏感字段）

### 2.4 AiToolExecutor

- 入口 `execute(toolName, argsJson)`：
  1. 从 `LoginUserHolder.get()` 取当前用户，按工具表调 `hasPermission(权限码)`；不通过直接抛「无权限」业务异常
  2. 参数反序列化为各工具的 DTO（record），做必填/长度校验
  3. 名称→id 解析（复用 SysDeptMapper/SysRoleMapper）
  4. 调用现有 `SysUserAdminService/SysRoleAdminService/SysDeptAdminService` 的 save 方法（不新写落库逻辑）
  5. 返回 `{ok:true, summary:"已创建部门 X（id=..）"}` 或 `{ok:false, error:"原因"}`
- 查询工具同样走权限校验（无权限的用户问"有哪些用户"时，模型会拿到"无权限"结果并礼貌解释）

### 2.5 AiChatService 编排循环

单次 `/ai/chat` 请求内：

1. 组装 system prompt（角色定位：sgy 管理系统助手；工具使用规则：查询直接调、新增只调工具不编造结果；中文回答）
2. 调 DeepSeekClient 流式输出：
   - 文本片段 → 直接透传给前端 SSE `delta` 事件
   - 收到 tool_calls：
     - **查询类**：逐个执行（含权限校验），把 `{role:"tool", tool_call_id, content:结果}` 追加进 messages，继续下一轮模型调用（最多 max-tool-rounds 轮，超出则让模型基于已有信息作答）
     - **新增类**：向后端 SSE 发 `toolcall` 事件（含 toolCallId、toolName、参数、确认卡片展示文案），然后 `done` 结束本次流；**绝不执行**
3. 任何一轮纯文本结束（finish_reason=stop）→ `done`

### 2.6 Controller 与 SSE 协议

`POST /api/ai/chat`，请求体：

```json
{ "messages": [ { "role": "user", "content": "..." } ] }
```

响应 `text/event-stream`，后端自定义事件（前端按事件名解析，不裸解析 DeepSeek 分片）：

```
event: delta
data: {"text":"好的"}

event: toolcall
data: {"toolCallId":"call_xxx","toolName":"create_dept","title":"创建部门","args":{"deptName":"财务部","parentName":"总公司"}}

event: done
data: {}

event: error
data: {"message":"AI 服务未配置 API Key"}
```

实现用 Spring MVC 的 `SseEmitter` + 独立线程跑编排循环；`onCompletion/onTimeout/onError` 时中断上游 HttpClient 请求；Emitter 超时 120s。

`POST /api/ai/tool/execute`，请求：

```json
{ "toolCallId": "call_xxx", "toolName": "create_dept", "args": { "deptName": "财务部" } }
```

返回标准 `R<{ok, summary}>`；无权限/参数错误走全局异常处理（与其他接口一致）。

两个接口都要求登录（JWT 过滤器全局生效，仅 /auth/** 放行）。

## 3. 前端设计

### 3.1 API 层 `src/api/ai/chat.ts`

- 类型：`AiChatMessage`（role/content/tool_calls/tool_call_id 四态联合）、`ToolCallPayload`
- `streamAiChat(messages, handlers, signal)`：
  - 统一用 **fetch + response.body.getReader() + TextDecoder** 读流（不用 axios responseType:stream）
  - 按空行切分事件块，解析 `event:`/`data:`，回调 onDelta/onToolCall/onDone/onError
  - 不在此层存状态
- `executeAiToolApi(payload)`：普通 JSON POST

### 3.2 组件 `src/components/ai-assistant/`

- `AiAssistant.vue`（单文件组件，职责集中）：
  - **悬浮按钮**：`fixed right-6 bottom-6`，56px 圆形，`/logo.png` 铺满，hover scale + 阴影；未读流式回复时按钮轻微呼吸动画
  - **聊天面板**：380px 宽、高 min(600px, 80vh)，右下角与按钮对齐，`v-if + Transition` 右滑入
  - 标题栏：logo + 「智能助手」+ 清空 + 关闭
  - 消息区：用户消息右侧主色气泡；助手左侧白底气泡（`whitespace-pre-wrap` 纯文本，第一版不引 Markdown 渲染器）
  - **工具确认卡片**（助手气泡内）：标题「请确认操作」、参数键值表格（label 中文化）、「确认执行」「取消」按钮；状态：待确认/已执行（显示 summary）/已取消/失败
  - 输入区：textarea 自适应高度，Enter 发送、Shift+Enter 换行；流式中显示「停止」按钮
  - 空状态：3 个推荐问题 chips（「查一下有哪些用户」「新增一个部门：财务部」「创建角色：财务专员」）
- 会话状态：组件内 ref，刷新即清空（第一版不做持久化、不做历史会话列表）
- 「单航道」策略：发起新请求前 `AbortController.abort()` 上一个；清空时同样 abort

### 3.3 确认交互时序（写操作）

```
用户："帮我在总公司下加个财务部"
  → 流式：助手先无正文，收到 toolcall(create_dept)
  → 渲染确认卡片（待确认）
用户点「确认执行」
  → executeAiToolApi → 成功 {summary}
  → 卡片置为已执行；messages 追加 assistant(tool_calls) + tool(summary)
  → 再次 streamAiChat → 模型流式："已在总公司下创建部门「财务部」（id=5）。"
用户点「取消」
  → 卡片置为已取消；messages 追加 tool("用户取消了该操作") → 模型回复"好的，已取消"
```

### 3.4 挂载位置

在 `src/layouts/basic.vue`（登录后主框架）挂载 `<AiAssistant />`；auth 布局不挂载 → 登录页不显示。

## 4. 错误处理

| 场景 | 行为 |
|---|---|
| 未配置 api-key | SSE 发 error 事件，助手气泡显示「AI 服务未配置，请联系管理员设置 DEEPSEEK_API_KEY」 |
| Key 失效/欠费（401/402/429） | 显示对应中文提示，不重试风暴 |
| 上游超时/网络错误 | 「AI 服务暂时不可用，请稍后重试」，保留已收到的片段 |
| 工具无权限 | 执行结果回喂模型，由模型用自然语言解释（如「你当前没有新增部门的权限」） |
| 参数缺失（如缺用户名） | 模型拿到错误后追问用户，不弹窗 |
| 前端断流/用户点停止 | AbortController 中断，已生成内容保留 |
| 消息超长 | 前端限制单条 1000 字；会话历史超过 20 条时只回传最近 20 条（控制 token） |

## 5. 安全边界

1. API Key 只存在于后端配置/环境变量，任何前端响应不包含
2. 所有工具执行在服务端按当前登录用户权限码重新校验——前端确认卡片只是 UX，不是安全边界
3. 写操作必须经用户显式确认（模型无法自行落库）
4. 工具结果做字段白名单，不回传密码等敏感字段
5. ai 接口同样被 JWT 过滤器与审计体系覆盖（写操作经 /tool/execute，可加 @OperLog）

## 6. 不做（YAGNI，后续迭代）

- 多轮会话持久化/历史列表、语音、Markdown 表格渲染、文件上传对话
- 修改/删除类工具、字典/库存模块工具
- 模型可选、流式 token 计费展示、Redis 缓存
