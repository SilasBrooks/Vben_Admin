# 变更提案：add-ai-menu-auth

## Why

AI 助手第一版只有 6 个工具（用户/角色/部门 的查询与新增），没有任何"给角色分配菜单"的工具。实测（2026-09-14）：AI 创建的角色在 `sys_role_menu` 中为 0 条、挂该角色的用户新登录后仅有 Dashboard，用户对 AI 说"分配菜单"时模型无从执行，感知为"分配菜单无效"。AI 建角色 → 授权 → 建用户是"对话式建号"闭环的最后一块缺口，本变更补齐它。

## What Changes

- 新增 `query_menus` 查询类工具（权限码 `System:Menu:List`）：返回菜单树（目录 M/菜单 C/按钮 F + 中文名 + 权限码 + 状态），供模型了解可分配范围
- 新增 `assign_role_menus` 写操作工具，走既有确认卡片链路（权限码 `System:Role:Auth`）：
  - 参数 `roleName`（角色中文名或 roleKey）+ `menuNames`（目录/菜单/按钮中文名数组）
  - 名称→id 解析：传目录名 = 该目录及全部后代（含按钮）；传菜单名 = 该菜单 + 全部祖先目录 + 其直属按钮；传按钮名 = 按钮 + 祖先链
  - 同名匹配到多个时报可读错误让模型追问，不猜测
  - 复用 `SysRoleAdminService.assignMenus`（全量重建语义），summary 明示"全量替换"
- 前端 `tool-labels.ts` 增加确认卡片中文标签；`AiChatService` system prompt 能力描述更新为 8 个工具
- 复用既有 SSE 流式、确认卡片、服务端权限校验链路，无数据库变更

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `ai/assistant`: 工具集从 6 个扩展到 8 个，新增菜单树查询与角色菜单授权（写操作确认语义）两项需求

## Impact

- **后端**：`module/ai/tool/AiTools` 注册 2 个工具定义；`AiToolExecutor` 新增 `queryMenus` / `assignRoleMenus` 实现（菜单名解析 + 父级补全，复用 SysMenuMapper 与 SysRoleAdminService）；`AiChatService` system prompt 更新
- **前端**：`components/ai-assistant/tool-labels.ts` 增加 `assign_role_menus` 参数中文标签（无新组件、无协议变化）
- **数据库**：无变更
- **安全**：`System:Menu:List` / `System:Role:Auth` 权限码校验沿用工具执行器既有逻辑；授权属全量替换写操作，必须经用户确认卡片执行
