## Context

`/menu/all` 通过 `SysMenuService` 按登录用户关联的启用角色和菜单生成路由树；`/user-config` 按登录用户和 key 读取 JSON 数组配置。两类响应均为数组，但菜单是权限结果，不能作为可自由保存的个人配置。

## Goals / Non-Goals

- Goals：统一菜单读取入口为 `/user-config?key=menu`，保留权限过滤、嵌套路由及菜单元数据；禁止覆盖只读菜单配置。
- Non-Goals：不修改菜单管理 CRUD、角色授权、数据库表结构和表格列配置协议。

## Decisions

- 用户配置服务验证登录态和 key 后，优先分发 `menu` 到 `SysMenuService.buildRouteTree`，转换成 JSON 数组；无菜单返回 `[]`。
- 保留 key 不读取 `sys_user_config`，即使存在同名旧配置也不能覆盖权限结果；保存 `menu` 返回双语业务错误 400。
- 前端用户配置读取 API 支持泛型返回类型，路由初始化直接以菜单路由数组类型请求 `menu`，移除独立菜单 API 与 Controller。
- 普通 key 继续使用既有存储、校验和幂等机制；无需迁移 SQL。

## Risks

- 旧前端仍调用 `/menu/all` 将无法初始化菜单：前后端同步更新，并更新现行文档中的接口地址。
- 将菜单持久化为普通个人配置可能绕过授权变化：保留 key 只读且每次读取按权限实时生成，并验证跨用户、无菜单、旧同名配置和非法写入场景。
