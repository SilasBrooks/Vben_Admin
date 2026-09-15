# Tasks: add-oper-login-log

## 1. 后端数据模型与种子

- [x] 1.1 `schema-h2.sql` / `schema-mysql.sql` 新增 `sys_oper_log`、`sys_login_log` 两表（design D3 字段）
- [x] 1.2 `module/monitor/entity` 新增 `SysOperLog`、`SysLoginLog` 实体 + Mapper（纯 MyBatis-Plus）
- [x] 1.3 `DatabaseSeeder` 增加「系统监控」M 目录、两个 C 菜单、4 个 F 按钮权限（Monitor:OperLog/LoginLog:List/Delete，authority super,admin）

## 2. 后端记录能力

- [x] 2.1 common 新增 `@OperLog(module, description)` 注解；`IpUtil`（X-Forwarded-For → remoteAddr）
- [x] 2.2 `OperLogAspect`：`@Around` 采集 → 脱敏/截断 → 异步落库（独立单线程 executor，静默吞异常，不阻塞业务）
- [x] 2.3 `LoginLogService.record()` 异步记录；`AuthController.login` 成功/失败两条路径埋点

## 3. 后端查询/清理接口

- [x] 3.1 `MonitorOperLogController`：GET list（操作人模糊/状态/时间范围分页）、DELETE {id}、DELETE clear；权限码 `Monitor:OperLog:List/Delete`
- [x] 3.2 `MonitorLoginLogController`：同上结构，权限码 `Monitor:LoginLog:List/Delete`

## 4. 给存量 controller 补注解

- [x] 4.1 用户/角色/菜单/部门四个 controller 的写操作方法标注 `@OperLog`（模块名与操作描述对齐）

## 5. 前端

- [x] 5.1 `api/monitor/log.ts`：list/delete/clear 两套 API
- [x] 5.2 `views/monitor/oper-log/index.vue`：分页列表 + 搜索（操作人/状态/时间范围）+ 删除/清空（v-access:code 控制按钮）
- [x] 5.3 `views/monitor/login-log/index.vue`：同结构
- [x] 5.4 路由/菜单确认（菜单由后端返回，前端无需静态路由；确认目录解析无误）

## 6. 验收

- [x] 6.1 重启后端（重置 H2）→ curl 登录 → 确认 sys_login_log 有成功记录；错误密码登录 → 失败记录
- [x] 6.2 curl 带权限调用新增部门 → oper_log 有记录且 params 脱敏；无 Monitor 权限账号（user）调 list → 403
- [ ] 6.3 浏览器：系统监控两页面分页/搜索/删除/清空正常；stockAdmin 侧边栏不可见监控菜单
