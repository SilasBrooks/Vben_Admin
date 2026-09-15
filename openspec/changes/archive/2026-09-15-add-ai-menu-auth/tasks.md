# 任务清单：add-ai-menu-auth

## 1. 后端：工具注册与执行器

- [x] 1.1 `AiTools` 注册 `query_menus`（QUERY，System:Menu:List，无参数）与 `assign_role_menus`（CREATE，System:Role:Auth，参数 roleName + menuNames[]），中文 description 写明解析规则与全量替换语义
- [x] 1.2 `AiToolExecutor` 实现 `queryMenus`：返回精简字段全集（id/menuName/menuType/parentId/perm/status），禁含敏感字段
- [x] 1.3 `AiToolExecutor` 实现 `assignRoleMenus`：resolveRoleId 复用 + resolveMenuIds（精确匹配、歧义报错、按 M/C/F 类型补全祖先链与直属按钮）+ 复用 `roleService.assignMenus` 全量重建；summary 明示全量替换与数量
- [x] 1.4 `AiChatService.SYSTEM_PROMPT` 更新为 8 个工具，补授权确认与全量替换提示；编译通过

## 2. 前端

- [x] 2.1 `tool-labels.ts` 增加 `assign_role_menus` 中文标签（目标角色/菜单）；`vue-tsc --noEmit` 通过

## 3. 验证与收尾

- [x] 3.1 curl/浏览器验证：问"有哪些菜单"自动执行查询；"给库存管理员分配库存管理"出确认卡片且库中未变更；确认后 sys_role_menu 写入目录+子菜单；"分配用户管理"自动带出系统管理目录与按钮码；同名歧义返回可读错误；无 System:Role:Auth 权限账号被拒绝
- [x] 3.2 归档 openspec 变更（sync delta → archive）
