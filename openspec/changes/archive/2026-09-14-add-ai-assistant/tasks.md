# 任务清单：add-ai-assistant

## 1. 后端：配置与 DeepSeek 客户端

- [x] 1.1 `application-dev.yml` 增加 `deepseek` 配置块（base-url/api-key/model/timeout/max-tool-rounds，api-key 支持 `DEEPSEEK_API_KEY` 环境变量覆盖）；新建 `AiProperties` 绑定，编译通过
- [x] 1.2 新建 `DeepSeekClient`：JDK HttpClient POST `/v1/chat/completions`（stream=true），解析 SSE 分片聚合 content 与 tool_calls（按 index 拼接 arguments），401/429/超时转中文错误；写一个临时 main/单测或 curl 验证能拿到 DeepSeek 流式回复

## 2. 后端：工具体系

- [x] 2.1 定义 6 个工具的 OpenAI tools JSON Schema（中文 description + 参数 DTO record：查询 keyword；新增各自字段），集中放在 `AiTools` 注册表
- [x] 2.2 `AiToolExecutor`：按工具映射权限码（System:User/role/Dept 的 List/Add），经 `LoginUser.hasPermission` 校验；名称→id 解析（部门名查树、角色名/key 匹配）；复用现有 Admin Service 完成查询与新增；返回精简结果 JSON
- [x] 2.3 编译通过，构造参数直接调用执行器验证：有权限建部门成功、无权限抛业务异常、名称解析失败返回可读错误

## 3. 后端：Agent 编排与接口

- [x] 3.1 `AiChatService`：system prompt + 编排循环（查询工具自动执行并回喂，最多 3 轮；新增工具发确认事件即停），文本片段逐字输出
- [x] 3.2 `AiController`：`POST /ai/chat` 返回 SSE（事件 delta/toolcall/done/error，SseEmitter 独立线程，客户端断开时中断上游）；`POST /ai/tool/execute` 标准 JSON，加 `@OperLog`
- [x] 3.3 后端编译启动，curl 验证：纯问答流式返回；"查一下有哪些部门"自动执行查询工具后作答；"新增角色财务专员"返回 toolcall 事件且库中未新增；确认执行后角色落库；无权限账号拿到无权限结果

## 4. 前端：流式 API 层

- [x] 4.1 `src/api/ai/chat.ts`：消息/工具调用类型、`streamAiChat`（fetch+ReadableStream 解析自定义 SSE 事件、支持 AbortController）、`executeAiToolApi`

## 5. 前端：悬浮助手组件

- [x] 5.1 `AiAssistant.vue`：右下角圆形 logo.png 悬浮按钮 + 右滑聊天面板（标题栏、消息气泡、空状态推荐问题、输入框 Enter/Shift+Enter、停止生成、清空）
- [x] 5.2 工具确认卡片：toolcall 渲染参数表格 + 确认/取消，确认后调 execute 接口、把 assistant(tool_calls)/tool 结果加回消息再发起一轮流式对话，卡片展示执行结果；错误态可见
- [x] 5.3 `layouts/basic.vue` 挂载组件（仅登录后布局可见）；`vue-tsc --noEmit` 通过

## 6. 收尾

- [x] 6.1 浏览器端到端验收：查询用户/部门、新增部门/角色/用户（确认→落库→页面刷新可见）、取消路径、停止生成、无权限账号（stockAdmin 问新增类问题）的提示
- [x] 6.2 docs/tech-overview.md 补 AI 助手模块说明；roadmap.md 增加「4.3 AI 智能助手 ✅」；归档 openspec 变更
