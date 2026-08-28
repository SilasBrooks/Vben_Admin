# Vben Admin 5 配套后端服务

为 [vue-vben-admin](https://github.com/vbenjs/vue-vben-admin)（front/，5.x）配套的企业级 RBAC 后端，**接口协议与官方 `apps/backend-mock` 完全一致**，前端零改造直接切换。

## 技术栈

| 项 | 说明 |
|---|---|
| JDK 21 + Spring Boot 3.5 | Web + Validation |
| MyBatis Plus 3.5 | ORM + 分页（`mybatis-plus-jsqlparser`） |
| JJWT 0.12 | 双 Token（access 7d + refresh 30d） |
| spring-security-crypto | 仅用 BCrypt 密码加密（无 Security 全家桶，链路透明可控） |
| H2（dev）/ MySQL 8（prod） | 开发零配置，生产一键切换 |
| 表设计 | 对齐若依 RuoYi-Vue 经典 RBAC 五表（47k star 生产验证） |

## 快速启动

```bash
cd service
mvn spring-boot:run
# 默认 dev profile：H2 内存库，自动建表 + 种子数据，无需安装数据库
```

前端（front/）已配置好代理，直接启动即可联调：

```bash
cd front
pnpm dev:ele        # 或 pnpm dev:antdv-next
```

> 前端 `vite.config.ts` 的 `/api` 代理已指向 `http://localhost:8080/api`，
> `.env.development` 的 `VITE_NITRO_MOCK` 已置为 `false`（切回官方 mock 则反向操作）。

### 内置账号（密码均为 123456，生产请立即修改）

| 账号 | 角色 | 可见页面（Demos 下） | 权限码 |
|---|---|---|---|
| vben | super | super-visible | AC_100100 / 110 / 120 / 010 |
| admin | admin | admin-visible | AC_100010 / 020 / 030 / System:User:List |
| jack | user | （无专属页） | AC_1000001 / AC_1000002 |

与 mock 的 MOCK_USERS / MOCK_MENUS / MOCK_CODES 数据完全对齐，三账号登录后的菜单、按钮、页面隔离行为与官方 mock 一致。

## 接口协议（与 backend-mock 逐一对齐）

统一响应体 `{code, data, error, message}`，`code === 0` 为成功（前端 `defaultResponseInterceptor` 约定）。

| 接口 | 说明 |
|---|---|
| `POST /api/auth/login` | 登录。返回 accessToken（响应体）+ refreshToken（httpOnly Cookie `jwt`）。失败 403 |
| `POST /api/auth/refresh` | 用 Cookie 换新 accessToken。**响应体为裸 token 字符串**（非 R 包装），失败 403 |
| `POST /api/auth/logout` | 清除 Cookie，幂等 |
| `GET /api/auth/codes` | 当前用户按钮级权限码（sys_menu.perm 汇总） |
| `GET /api/user/info` | `{id, username, realName, roles, homePath}` |
| `GET /api/menu/all` | 当前用户动态路由树（backend 菜单模式） |
| `GET /api/system/user/list` | 样板业务接口：分页 + `@RequirePermission("System:User:List")` |

无 token 访问受保护接口返回 401（前端自动尝试 refresh）；权限不足返回 403。

## 表设计（若依 RBAC 模型）

```
sys_user ──< sys_user_role >── sys_role ──< sys_role_menu >── sys_menu
```

- `sys_menu.menu_type`：`M` 目录 / `C` 菜单 / `F` 按钮
  - M/C 生成前端动态路由（`/menu/all`）；F 不进路由树，其 `perm` 汇入权限码（`/auth/codes`）
- `sys_menu` 融合 vben meta 字段：`title`（支持 i18n key）、`icon`、`order_num`、
  `keep_alive`、`affix_tab`、`visible`（hideInMenu）、`authority`（meta.authority）、
  `extra_meta`（JSON，任意 meta 扩展如 badge / menuVisibleWithForbidden）
- `component` 存前端页面路径（如 `/dashboard/analytics/index`，对应 `../views/dashboard/analytics/index.vue`）
- `sys_user.home_path` 登录后首页；`sys_role.role_key` 即前端 `roles` 数组元素

## 如何扩展（企业业务标配套路）

1. **加页面**：前端 `views/` 下新增 `xxx/index.vue` → `sys_menu` 插一条 C 记录
   （`component=/xxx/index`）→ 角色授权
2. **加按钮权限**：`sys_menu` 插 F 记录（`perm=Module:Entity:Action`）→ 角色授权 →
   前端用 `v-access` / `AccessControl` 控制，后端用 `@RequirePermission` 校验
3. **加业务接口**：仿照 `SysUserController`——`@RestController` + `@RequirePermission` +
   MyBatis Plus 分页，返回 `R.ok({items, total})`

## 生产部署检查清单

1. **MySQL**：建库 `vben_service` → 执行 `db/schema-mysql.sql` →
   `application-prod.yml` 配置数据源（`spring.sql.init.mode` 首次 `always` 后改 `never`）
2. **JWT 密钥**：环境变量 `VBEN_JWT_ACCESS_SECRET` / `VBEN_JWT_REFRESH_SECRET`
   （≥32 字节随机串，access/refresh 不同值）
3. **Cookie**：`vben.cookie.same-site=None` + `secure=true`（https 跨域），
   本地 http 联调必须 `Lax` + `false`，否则浏览器拒收 refresh Cookie
4. **CORS**：`vben.cors.allowed-origins` 配置真实前端域名（走同源代理则无需）
5. **默认账号**：上线前修改/禁用种子账号，或删除 `DatabaseSeeder`
6. **日志**：`mybatis-plus.configuration.log-impl` 已在 prod 关闭 SQL 打印

## 目录结构

```
service/src/main/java/com/vben/service/
├── ServiceApplication.java        # 启动类（含接口协议说明）
├── common/                        # R 统一响应、BizException、全局异常
├── config/                        # Web 配置（过滤器/拦截器/CORS）、MP 分页、属性绑定
├── security/                      # JWT 双 Token、认证过滤器、@RequirePermission 权限拦截
├── bootstrap/DatabaseSeeder.java  # 种子数据（空库自动执行一次）
└── module/
    ├── auth/                      # /auth/**（login/refresh/logout/codes）+ Cookie 服务
    ├── user/                      # /user/info
    ├── menu/                      # /menu/all + sys_menu→vben 路由树转换
    └── system/                    # RBAC 实体/Mapper/服务 + 样板用户管理接口
```
