# Vben Admin 管理系统 —— 技术架构全景说明

> 更新时间：2026-09-02
> 本文档由代码结构盘点生成，覆盖前端、后端全部技术模块，以及各模块的职责、实现位置与现状。

---

## 一、整体架构

```
┌─────────────────────────────┐       ┌──────────────────────────────┐
│  前端 web-ele (Vite, :5777) │  /api │  后端 vben-service (:8080)   │
│  Vue3 + Element Plus        │ ────► │  Spring Boot 3.5 (JDK 21)    │
│  vben-admin v5.7 monorepo   │ 代理  │  MyBatis-Plus                │
└─────────────────────────────┘       └──────────┬───────────────────┘
                                                 │
                                        ┌────────▼────────┐
                                        │ PostgreSQL(开发) │  ←Docker 容器 vben5 :5444/vben5
                                        │  MySQL(生产就绪) │  ←schema-mysql.sql
                                        └─────────────────┘
```

- **前后端分离**：前端 dev server 将 `/api` 前缀代理到后端 8080；生产走 nginx。
- **当前无中间件**：没有 Redis、MQ、ES；缓存均在应用内存（如字典 hook 的模块级缓存），会话无状态（JWT）。
- **认证模型**：JWT 双 Token（accessToken + refreshToken），服务端完全无状态。

---

## 二、后端（`service/`）

### 2.1 基础技术栈

| 技术 | 版本 | 用途 | 位置 |
|---|---|---|---|
| Java | 21 | 运行时 | `pom.xml` |
| Spring Boot | 3.5.6 | Web / Validation / AOP 三大 starter | `pom.xml` |
| MyBatis-Plus | 3.5.12 (+jsqlparser) | ORM、分页插件、逻辑封装 | `config/MybatisPlusConfig.java` |
| jjwt | 0.12.6 | JWT 签发与校验（双 Token） | `security/JwtTokenService.java` |
| spring-security-crypto | 随 Boot | 仅用 BCrypt 密码加密（**未引入完整 Spring Security**，授权走自定义拦截器） | `pom.xml` |
| postgresql / mysql-connector-j | 随 Boot | 开发库（PG 18, Docker）/ 生产库驱动 | `resources/application-*.yml` |
| Lombok | 随 Boot | 实体样板代码 | 全部实体 |

### 2.2 统一横切层（`common/`）

| 组件 | 职责 |
|---|---|
| `R.java` | 统一响应包装 `{code, data, error, message}`，`code=0` 表示成功 |
| `BizException` + `GlobalExceptionHandler` | 业务异常 → 统一错误码；参数校验异常兜底 |
| `OperLog.java`（注解）+ `OperLogAspect.java`（切面） | 声明式操作审计：标注在 Controller 方法上，自动记录模块/描述/操作人/方法/入参/结果/耗时/IP；密码类字段脱敏为 `***`，入参截断 2000 |
| `LogExecutor.java` | 单线程异步落库日志（队列满丢弃新日志、异常静默，绝不影响业务主流程） |
| `IpUtil` | 从请求头/RemoteAddr 提取真实客户端 IP |

### 2.3 认证与授权（`security/` + `module/auth/`）

**认证流程（JWT 双 Token 无状态方案）：**

```
POST /api/auth/login (username+password, 滑块验证为前端行为)
  → BCrypt 校验 → 签发 accessToken(短效) + refreshToken(长效, HttpOnly Cookie)
  → 前端存 accessToken；过期后用 refreshToken 静默续期
后续请求：JwtAuthFilter 解析 Bearer Token → LoginUser 载入 LoginUserHolder(ThreadLocal)
```

| 组件 | 职责 |
|---|---|
| `JwtTokenService` | 双 Token 签发/解析（access + refresh 独立密钥，配置于 `application*.yml`） |
| `JwtAuthFilter` | 每请求解析 Token，构造 `LoginUser`（含 userId/username/roles/permissionCodes） |
| `LoginUserHolder` | ThreadLocal 持有当前登录用户，service 层随处可取 |
| `RequirePermission`（注解）+ `PermissionInterceptor` | 接口级授权：`@RequirePermission("System:User:Edit")`，无权限返回 403 |
| `RefreshTokenCookieService` | refreshToken 写入/清除 HttpOnly Cookie |
| `AuthController` | login / logout / refresh / codes(权限码列表) 接口 |

### 2.4 业务模块（`module/`）

| 模块 | 内容 | 关键约定 |
|---|---|---|
| `module/system` | 用户/角色/菜单/部门/字典 五大管理模块，Controller + Entity + Mapper + AdminService 分层 | 写接口全部挂 `@RequirePermission` + `@OperLog`；同级重名校验、防环校验（部门）、级联删除（菜单/字典/角色授权） |
| `module/menu` | 面向前端的路由服务：`/menu/all` 按当前用户角色过滤后输出 vben 路由树（`VbenRoute`），是**动态路由/菜单权限**的数据源 | authority 字段 `super,admin` 控制可见性 |
| `module/monitor` | 操作日志 / 登录日志：两表 + 分页查询 + 单条删除 + 清空 | 权限码 `Monitor:*:List/Delete`；登录成功失败均记 IP/UA |
| `module/ai` | AI 智能助手：DeepSeek 流式对话 + Function Calling 工具集（8 个：用户/角色/部门/菜单查询自动执行，用户/角色/部门新增与角色菜单授权确认后执行，授权为全量替换并自动补全父级）；SSE 事件 delta/history/toolcall/done/error | API Key 仅存后端配置；写操作复用现有 Admin Service + 权限码校验；JDK HttpClient 零新依赖 |
| `module/user` | `/user/info`（当前用户信息）、`/auth/codes`（权限码）等对接 vben 的桥接接口 | |
| `bootstrap/DatabaseSeeder` | 空库初始化：3 角色账号（vben/admin/jack/stockAdmin）、菜单树+按钮权限码、部门树、库存状态字典 | 仅 `sys_user` 空表时执行 |

### 2.5 配置（`resources/`）

- `application.yml`：激活 `dev`；JWT 密钥、Token 有效期、上传路径等经 `VbenProperties` 绑定
- `application-dev.yml`：PostgreSQL（`jdbc:postgresql://127.0.0.1:5444/vben5`，Docker 容器 vben5）+ `schema-postgres.sql` 幂等初始化；`update_time` 由 MyBatis-Plus MetaObjectHandler 应用层填充
- `application-prod.yml`：MySQL + `schema-mysql.sql`，JWT 密钥走环境变量（`VBEN_JWT_ACCESS_SECRET` 等）
- **角色与权限种子**：super(全部) / admin(管理) / user(普通) / stockAdmin(仓储部受限演示号)

### 2.6 当前未引入（后端）

Redis、消息队列、Spring Security 完整体系、SpringDoc 接口文档、文件上传服务、验证码后端校验、数据权限拦截器。

---

## 三、前端（`front/`，pnpm monorepo）

### 3.1 monorepo 结构（vben-admin v5.7.0）

```
front/
├── apps/web-ele/          ← 实际运行的应用（Element Plus 版）
├── packages/@core/        ← 框架核心（与 UI 库无关）：preferences、router、stores、
│                             request、access、ui-kit(shadcn-ui/menu-ui/vxe-ui...)
├── packages/effects/      ← 业务效果层：layouts(基础/认证布局)、plugins(vxe-table)、access
├── packages/utils、icons、locales、styles、types ...
└── internal/              ← 工程化：vite-config(含启动 loading 注入插件)、lint-configs、tsconfig
```

### 3.2 web-ele 应用技术栈

| 技术 | 用途 |
|---|---|
| Vue 3 + `<script setup>` + TS | 全部业务页面 |
| Vite | dev :5777，`/api` 代理到 8080；`loading.html`（应用级模板）注入启动全屏动画 |
| Element Plus | UI 组件库（自动按需导入 unplugin-element-plus） |
| Pinia（@vben/stores 封装） | accessStore(token/权限)、tabbar、userStore；业务 store 在 `src/store/auth.ts` |
| vue-router | 动态路由 + 全局守卫 |
| @vben/request（axios 封装） | baseURL 指向 apiURL；请求头注入 token；**401 自动用 refreshToken 续期，失败再弹登录过期弹窗/登出**（`src/api/request.ts`） |
| @vben/access | `v-access:code` 按钮权限指令 + `hasAccessByCodes`；`accessMode: 'backend'` 模式下从 `/menu/all` 拉取路由 |
| @vben/preferences | 主题/布局偏好，localStorage 持久化，合并顺序 overrides > cache > default |
| vxe-table（plugins 封装） | 列表页统一表格：搜索表单、分页、工具栏（全局适配器 `src/adapter/vxe-table.ts`） |
| @vben/locales | i18n（zh-CN / en-US） |
| Tailwind CSS | 原子化样式 |

### 3.3 应用层代码（`apps/web-ele/src/`）

| 目录 | 内容 |
|---|---|
| `adapter/` | 表单组件适配器（Input/TreeSelect/RadioGroup…）、vxe-table 全局适配（useVbenVxeGrid 搜索默认不显示展开/收起按钮） |
| `api/` | 按模块组织：`core/`(auth/menu/user)、`system/`(用户/角色/菜单/部门/字典)、`monitor/`(日志)；`request.ts` 是 axios 客户端工厂 |
| `hooks/use-dict.ts` | **字典实例**：`useDict('wsm_stock_status')` 返回响应式选项数组，模块级缓存 + 字典变更自动刷新 |
| `router/` | `core.ts` 固定路由（登录/404 等，404 兜底包在 BasicLayout 内）；`access.ts` 生成动态路由；`guard.ts` 登录/权限守卫 |
| `store/auth.ts` | 登录、拉取用户信息与权限码、登出 |
| `views/system/` | 用户/角色（含角色-菜单授权弹窗）/菜单（树形表格 childrenField 模式）/部门（树形）/字典（类型列表+数据弹窗）五大管理页，按钮均挂 `v-access:code` |
| `views/monitor/` | 操作日志、登录日志列表页 |
| `views/wsm/` | 库存业务：store（字典驱动状态列+搜索下拉的示例页）、transport |
| `views/_core/` | 认证页（含滑块验证码——**目前仅前端**）、个人中心模板页（未接后端）、fallback 页 |
| `preferences.ts` | 项目级偏好覆盖（app 名称、`accessMode: 'backend'` 等） |

### 3.4 关键前端机制

- **权限双轨**：路由级 = 后端菜单树过滤（不返回则无路由，访问即布局内 404）；按钮级 = `v-access:code` 对比 `/auth/codes` 返回的权限码集合。
- **Token 续期**：响应拦截器遇 401 → `refreshTokenApi` → 重放原请求；refresh 也失效 → 按偏好弹「登录过期」弹窗或登出。
- **主题持久化**：preferences 合并顺序保证用户缓存的色板/暗色模式刷新不丢失。

---

## 四、数据库（开发 PostgreSQL，生产就绪 MySQL）

| 表 | 用途 |
|---|---|
| sys_user / sys_role / sys_user_role | 用户、角色、用户-角色 |
| sys_menu | 菜单+按钮权限码（M/C/F 三型，authority 控制可见，perm 存权限码） |
| sys_role_menu | 角色-菜单授权 |
| sys_dept | 部门树（parentId 自引用） |
| sys_user.dept_id | 用户归属部门 |
| sys_role.data_scope / sys_role_dept | 角色数据范围（1全部/2自定义/3本部门/4本部门及以下/5仅本人）+ 自定义部门关联 |
| sys_dict_type / sys_dict_data | 字典类型/数据 |
| sys_oper_log / sys_login_log | 操作/登录日志 |

Schema 双版本：`schema-postgres.sql`（dev 自启）与 `schema-mysql.sql`（prod）。

---

## 五、账号与权限现状

| 账号 | 角色 | 演示效果 |
|---|---|---|
| vben/123456 | super | 全部菜单与权限码，数据范围恒为全部数据 |
| admin/123456 | admin | 管理类菜单与权限 |
| jack/123456 | user | 仅 Dashboard |
| stockAdmin/123456 | stock + 仓储部 | 用户列表按数据范围过滤（本部门及以下），可配自定义部门 |

数据范围（Data Scope）：`@DataScope` 注解 + `DataScopeAspect` 切面把当前用户可见部门集合放入 `DataScopeHolder`（ThreadLocal），业务查询按需追加过滤条件；多角色取并集，部门类范围恒包含未归属数据与本人。

---

## 六、待补能力（roadmap 摘录）

个人中心自助改密/改资料、验证码后端校验+登录限流、文件上传、系统参数配置、首页工作台、通知公告、SpringDoc 接口文档；生产化三件套：PostgreSQL/Redis/Docker。详见 `openspec/roadmap.md`。
