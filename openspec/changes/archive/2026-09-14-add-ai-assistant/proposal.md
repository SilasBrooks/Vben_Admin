# 变更提案：add-ai-assistant

## Why

当前系统所有操作都必须由用户自己找到菜单、打开页面、填写表单完成。市面上主流后台（钉钉/飞书/各类 SaaS）普遍提供右下角 AI 助手：自然语言提问即可查数据、下指令即可建数据，降低新用户的操作门槛，也是本项目「2026 主流后台架构」缺失的智能交互入口。第一版接入 DeepSeek（OpenAI 兼容协议、国内可直连、成本低），跑通「对话 → 识别意图 → 确认 → 执行 → 反馈」闭环。

## What Changes

- 全局右下角悬浮 AI 入口：圆形按钮使用 `public/logo.png`，登录后布局内固定显示（登录页不显示），点击从右侧滑出聊天面板
- 聊天面板：消息流（打字机流式输出）、输入框（Enter 发送/Shift+Enter 换行）、停止生成、清空会话、空状态推荐问题
- 后端新增 AI 代理模块（DeepSeek API Key 只存后端，前端不可见）：
  - `POST /api/ai/chat`：SSE 流式转发 DeepSeek 回复（JDK 内置 HttpClient，零新依赖）
  - `POST /api/ai/tool/execute`：用户确认后执行写操作
- 采用 DeepSeek 官方 Function Calling（tools）机制，不做正则解析 JSON
- 第一版 6 个工具（用户/角色/部门 的查询 + 新增）：
  - 查询类（query_users / query_roles / query_depts）：模型调用后**后端自动执行**，结果回喂模型继续作答
  - 新增类（create_user / create_role / create_dept）：模型只产出结构化调用，后端**暂停并下发确认卡片**，用户点「确认执行」后才真正落库
- 每个工具服务端强制校验当前登录用户的功能权限码（System:User:List/Add 等），越权调用返回可读错误并由模型解释
- 会话历史前端持有（无状态后端，不引入 Redis/会话表）；支持按部门名/角色名自然语言传参，后端解析为 id

## Capabilities

### New Capabilities

- `ai/assistant`: 智能助手能力——悬浮入口与会话面板交互、SSE 流式对话协议、Function Calling 工具集（查询/新增两档语义）、写操作确认机制、服务端权限校验边界、错误处理

## Impact

- **后端**：新增 `module/ai` 包（配置属性、DeepSeek 客户端、工具注册/执行器、Agent 编排服务、SSE Controller）；`application-dev.yml` 新增 `deepseek.*` 配置；复用现有用户/角色/部门 Admin Service
- **前端**：新增 `api/ai/chat.ts`（fetch + ReadableStream 流式客户端）与 `components/ai-assistant/` 悬浮组件；`layouts/basic.vue` 挂载组件
- **数据库**：无变更
- **安全**：API Key 仅存后端配置/环境变量；写操作二次确认；工具执行复用现有权限码体系
