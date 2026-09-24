## MODIFIED Requirements

### Requirement: 本人配置存取

系统 SHALL 提供 GET `/user-config`（query 参数 key）与 POST `/user-config/save`（JSON 对象 key、value，value 为 JSON 数组）。系统 MUST 从登录态获取用户身份，不允许客户端指定其他用户。`menu` MUST 为只读保留 key，读取时根据当前用户权限生成菜单路由数组，保存时返回 400。其他 key MUST 按当前用户读取或覆盖持久化配置，未保存返回空数组，保存空数组表示恢复默认。系统 MUST 校验 key、数组结构与大小，并通过唯一约束确保同一用户同一持久化 key 只有一条配置。

#### Scenario: 保存与恢复
- **WHEN** 已登录用户保存某非保留 key 的列配置后再次获取
- **THEN** 返回同一 JSON 数组，重新登录或更换设备仍可获取

#### Scenario: 用户和表格隔离
- **WHEN** 两个用户保存同一非保留 key，或同一用户保存不同非保留 key
- **THEN** 各自配置独立，读取只返回本人对应 key 的配置

#### Scenario: 未认证或非法请求
- **WHEN** 未登录访问或提交非法 key、非数组、超大配置
- **THEN** 未登录返回 401，非法输入返回 400 且不保存

#### Scenario: 保存只读菜单配置
- **WHEN** 已登录用户向 `/user-config/save` 提交 key 为 `menu` 的请求
- **THEN** 返回 400 与支持中英文的只读提示，不写入个人配置或修改菜单权限

## ADDED Requirements

### Requirement: 菜单配置统一读取

系统 SHALL 通过 GET `/user-config?key=menu` 返回当前登录用户有权访问的动态路由树，并移除原 GET `/menu/all` 接口。菜单 MUST 根据当前用户关联的启用角色与启用菜单生成，保留既有嵌套 children、路由字段和 meta；按钮权限 MUST NOT 出现在菜单路由数组中。系统 MUST 忽略 `sys_user_config` 中同用户同名 `menu` 配置，无可用菜单时返回空数组。前端菜单初始化 MUST 使用统一用户配置接口，保持既有初始化、路由注册和失败重试行为。

#### Scenario: 按当前用户返回权限菜单
- **WHEN** 不同权限的已登录用户请求 key 为 `menu` 的配置
- **THEN** 分别返回各自有权访问的动态路由树，客户端提交 userId 不得改变目标用户

#### Scenario: 授权变化和同名个人配置
- **WHEN** 用户授权变化后重新获取菜单，或个人配置表中存在同名 `menu` 记录
- **THEN** 按最新授权生成菜单，不使用同名个人配置覆盖权限结果

#### Scenario: 没有可用菜单
- **WHEN** 当前用户没有可访问的菜单
- **THEN** 返回 JSON 空数组而非 null

#### Scenario: 前端初始化
- **WHEN** 前端为已登录用户初始化动态菜单
- **THEN** 请求 `/user-config?key=menu`，按返回数组生成菜单与动态路由，不再请求 `/menu/all`
