# delta: ai/assistant

## MODIFIED Requirements

### Requirement: 工具集与两档执行语义

系统 MUST 内置覆盖系统管理、监控、内容、消息聊天等模块的 AI 工具集，全部以 `@AiAgentTool` 注解声明在 `module/ai/tool/` 下的 `@Component` 类中，启动时反射扫描自动注册，新增工具 MUST NOT 修改注册表或分发代码。工具按功能域分组：

- 系统管理查询/写入：用户、角色、部门、菜单、字典、文件的查询与用户/角色/部门/公告创建，用户/角色/部门删除，密码重置，角色菜单授权（高危）
- 监控查询：在线用户、登录日志、操作日志、仪表盘统计
- IM 消息聊天：联系人、会话、历史查询与消息发送（见「IM 消息聊天工具」需求）

- **查询类工具**：当模型发起调用时，后端 MUST 在同一次请求内自动执行、把结果回喂模型继续作答，MUST NOT 要求用户确认。
- **写操作类工具**：后端 MUST NOT 直接执行，MUST 通过 toolcall 事件把结构化参数下发给前端展示确认卡片；仅当用户显式确认后，经 `POST /api/ai/tool/execute` 才可落库。高危工具在多步计划中另需二次确认。

工具参数 MUST 支持以中文名称或登录名指代引用对象（如部门名、角色名、用户名），由后端解析为实际 id；无法解析时 MUST 返回可读错误让模型追问，MUST NOT 猜测或自动创建引用对象。

#### Scenario: 查询自动执行

- **WHEN** 用户问"系统里有哪些部门"
- **THEN** 后端自动执行部门查询并由模型基于真实数据流式回答，全程不出现确认卡片

#### Scenario: 新增必须确认

- **WHEN** 用户说"帮我新增一个角色叫财务专员"
- **THEN** 对话框出现包含角色名等参数的确认卡片，且在用户点击确认前数据库中 MUST NOT 出现该角色

#### Scenario: 确认后执行并反馈

- **WHEN** 用户在确认卡片上点击"确认执行"
- **THEN** 后端完成落库，卡片显示执行结果，随后模型用中文汇报创建结果（含新对象标识）

#### Scenario: 取消新增

- **WHEN** 用户在确认卡片上点击"取消"
- **THEN** 不发生任何数据变更，卡片标记为已取消，助手确认操作已取消

## ADDED Requirements

### Requirement: IM 消息聊天工具

系统 MUST 提供 4 个 IM 消息聊天工具，全部为登录即可调用（无权限码，与 IM REST 接口语义一致）：

- `query_im_contacts` 查询类：返回全部启用状态的其他用户（username、nickname），供模型确定消息接收人。
- `query_my_conversations` 查询类：返回当前用户会话列表（对方 username/nickname、最后一条消息、未读数），最近活跃在前。
- `query_chat_history` 查询类：参数 username（对方登录名）与可选 limit（默认 20，上限 50），返回与该对方的聊天记录（时间正序，含发送方向、内容、是否已读、时间）。
- `send_chat_message` 写操作类：参数 username 与 content（≤500 字），MUST 经 toolcall 确认卡片、用户显式确认后经 `ImChatService.send` 落库并实时推送；执行结果 summary MUST 包含接收人与消息内容概要。

username 解析 MUST 精确匹配唯一用户；0 个或多个匹配 MUST 返回可读错误让模型追问。发送工具 MUST 复用既有校验：接收人不存在或停用即失败、MUST NOT 允许给自己发消息。查询结果 MUST 仅覆盖本人参与的消息（单侧删除的消息按既有语义过滤）。

#### Scenario: 查询联系人自动执行

- **WHEN** 用户问"我能给谁发消息"
- **THEN** 后端自动执行联系人查询并由模型基于真实数据作答，全程不出现确认卡片

#### Scenario: 发消息必须确认

- **WHEN** 用户说"给 jack 发条消息说明天开会"
- **THEN** 对话框出现包含接收人 jack 与消息内容的确认卡片，用户确认前数据库中 MUST NOT 出现该消息

#### Scenario: 确认后发送成功

- **WHEN** 用户在确认卡片上点击"确认执行"
- **THEN** 消息落库且对方在线时实时送达，summary 明示「已发送给 jack」，模型据此汇报

#### Scenario: 用户名不存在追问

- **WHEN** send_chat_message 或 query_chat_history 的 username 匹配不到用户
- **THEN** 不执行任何操作，返回可读错误由模型向用户追问确认接收人
