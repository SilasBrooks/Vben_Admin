# 技术设计：add-ai-menu-auth

## Context

AI 助手（已归档 add-ai-assistant）具备完整的工具链路：`AiTools` 注册表 → `AiToolExecutor`（权限校验 + 名称→id 解析 + 复用 Admin Service）→ `AiChatService` 编排（查询自动执行/新增走确认卡片）→ 前端确认卡片。菜单授权所需的后端能力已存在：`SysMenuMapper`（全表查询）、`SysRoleAdminService.assignMenus(roleId, menuIds)`（先删后插全量重建）。本变更纯增量：注册 2 个工具 + 执行器实现，不新增接口、不新增组件、不动协议。

## Goals / Non-Goals

**Goals:**

- 模型可查询菜单全集，可对指定角色执行菜单授权（经确认卡片）
- 名称解析对"目录/菜单/按钮"三种粒度都有直观语义，并自动补全祖先链，保证授权结果在动态路由树中完整可用
- 全程复用既有权限校验与确认链路，零协议变化

**Non-Goals:**

- 不做菜单管理（新增/修改/删除菜单）的 AI 工具
- 不做"给用户分配角色"的更新类工具（创建时已支持 roleNames）
- 不做增量授权（勾选/取消部分菜单），只做全量替换

## Decisions

### 1. 工具定义（AiTools 注册表）

| 工具名 | 类别 | 权限码 | 参数 |
|---|---|---|---|
| query_menus | QUERY | System:Menu:List | 无（返回全集，约 40 条，无需分页/关键字） |
| assign_role_menus | CREATE | System:Role:Auth | roleName（角色中文名或 roleKey）、menuNames（string[]） |

- `assign_role_menus` 归入 CREATE 类：复用"toolcall 下发确认卡片 → 用户确认 → /ai/tool/execute → runStrict"整条链路，编排循环零改动
- query_menus 不设 keyword：菜单全集数量小（种子 38 条），全集返回反而让模型建立完整结构认知，利于后续授权参数生成

### 2. 名称→id 解析与父级补全（执行器核心逻辑）

新增私有方法 `resolveMenuIds(List<String> menuNames)`：

1. 全量加载 `sys_menu`（一次查询，内存建 id→SysUser 索引与 parentId 索引）
2. 对每个名称做**精确匹配**（menuName）：
   - 0 个匹配 → 抛「未找到名为 X 的菜单，请先用查询菜单工具确认」
   - 多个匹配 → 抛「存在多个名为 X 的菜单（列出各自上级路径），请提供更明确的名称」
3. 按命中节点类型补全：
   - M 目录 → 该目录 + 全部后代（递归收集，含 F 按钮）
   - C 菜单 → 该菜单 + 全部祖先目录链 + 其直属 F 按钮
   - F 按钮 → 该按钮 + 全部祖先目录链
4. 结果去重后作为最终 menuIds 集合

角色解析复用 `resolveRoleId`（已有，roleKey/roleName 精确匹配 + 歧义报错）。

### 3. 全量替换语义与结果反馈

- 落库直接调 `roleService.assignMenus(roleId, menuIds)`，保持与 UI 授权完全一致（同一条代码路径，不新写 SQL）
- summary 明确写「已为角色「X」重新分配 N 项菜单（全量替换，原授权已清除）」，卡片可读
- 模型侧 description 写明"全量替换"语义，引导模型在授权前先用 query_roles/沟通确认用户意图，避免误清空

### 4. 前端与提示词

- `tool-labels.ts`：`assign_role_menus` → { roleName: 目标角色, menuNames: 菜单 }（数组 join「、」展示，已有逻辑支持）
- `AiChatService.SYSTEM_PROMPT`：能力范围更新为 8 个工具，补充"授权前先与用户确认要分配哪些菜单；授权是全量替换"

## Risks / Trade-offs

- **同名菜单歧义**：种子数据无重名，但用户自建菜单可能重名 → 解析阶段显式报错列出路径，由模型追问，不静默猜测
- **全量替换误操作**：模型漏带旧菜单会清掉已有授权 → summary 与模型提示词双重明示替换语义；确认卡片展示完整菜单清单供用户人工把关（确认卡片本身是安全边界之外的 UX，权限校验仍在服务端）
- **菜单全集过大**：当前规模（≈40 条）返回全集无压力；若未来菜单数膨胀，可在 query_menus 增加 keyword 过滤（YAGNI 暂不做）
