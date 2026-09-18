# add-profile-page 技术设计

## Context

- 后端已有 `GET /user/info`（UserController，登录即可）、`POST /auth/change-password`（AuthController，成功 bump token 版本号）、`POST /file/avatar`（FileController，仅图片 5MB + 限流 10/60s，成功后自动更新本人 sys_user.avatar）。三者均可复用，不改。
- `sys_user` 无 `introduction` 列；nickname 目前仅用户管理接口可改。
- 前端 `views/_core/profile/` 为模板空壳：`Profile` 路由未注册（core.ts 里没有），`base-setting.vue` 用 mock 角色、`password-setting.vue` 提交只弹假提示，`security-setting.vue`/`notification-setting.vue` 是纯假数据。
- 页面骨架 `index.vue` 使用 `@vben/common-ui` 的 `Profile` 组件（title/tabs/user-info props + 插槽渲染各 tab），保留该骨架，只重写 tab 内容。

## Goals / Non-Goals

**Goals:**

- 本人自助改昵称、简介（新接口 PATCH /user/profile）
- sys_user 增加 introduction 列（PostgreSQL 增量 + MySQL 同构）
- 前端 Profile 路由可用，基本设置/修改密码两个 tab 全走真实接口
- 头像上传成功后 header 与页面即时同步

**Non-Goals:**

- 不做登录设备管理、密保手机/邮箱、消息通知设置（无后端支撑，假数据 tab 直接删除）
- 不改 change-password、file/avatar 现有行为
- 不做管理员代改他人资料的入口变化（用户管理页已有）

## Decisions

### D1: 改资料接口用 PATCH /user/profile，控制器放 UserController

`UserController` 目前只有 `GET /user/info`，同为"本人自助"语义，与 /user/info 同放一处（而非塞进 admin 语义的 SysUserController 或 auth 模块）。无需权限码，`LoginUserHolder.require()` 取本人 id，与 /user/info、/auth/change-password 的既有模式一致。DTO 校验 nickname @NotBlank、introduction 无限制（可空，长度上限 VARCHAR 容量内）。

备选：复用 SysUserController 的 PATCH /users/{id}——需要 System:User:Edit 权限码，本人无权限时反而改不了自己，否决。

### D2: introduction 加列方式——沿用 schema 文件的增量风格

`db/schema-postgres.sql` 已采用 `CREATE TABLE IF NOT EXISTS` + `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` 幂等风格（avatar 列即此模式），照抄：`ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS introduction VARCHAR(255) NULL` + COMMENT。MySQL 用其既有的 information_schema 判断 + 预置 SQL 方式（avatar 列同款）。实体 `SysUser` 加 `private String introduction;`。`sql.init always` 重启自动补列，无需手工迁移。

### D3: /user/info 的 introduction 兜底为空字符串

`avatar` 现有逻辑是 null 时不放 key；`introduction` 若同样省略，前端表单 setValues 收到 undefined 与空串行为一致，但规格要求空串。返回 `data.put("introduction", user.getIntroduction() == null ? "" : user.getIntroduction())`。

### D4: 前端路由注册在 core.ts 的 BasicLayout children 中

与 404 fallback 同文件、同模式：`{ name: 'Profile', path: '/profile', component: () => import('#/views/_core/profile/index.vue'), meta: { title: $t('page.auth.profile'), hideInMenu: true } }`。hideInMenu 避免侧边栏出现与动态菜单体系无关的固定项。登录即可，无权限码。

### D5: 基本设置保留 ProfileBaseSetting 组件骨架，formSchema 换为真实字段

沿用 `@vben/common-ui` 的 `ProfileBaseSetting`（含头像区域 + 表单布局），formSchema 改为：昵称 Input（必填）、个人简介 Textarea（用 `Input` + `type: 'textarea'`，项目已知教训：未注册的 Textarea 组件会导致输入失效）、用户名/角色只读不进表单而在头像下方展示。头像上传用原生 input[type=file] + uploadAvatarApi（accept=image/*），成功后调用 userStore 刷新 userInfo（改资料同理），header 头像来自 `userStore.userInfo?.avatar` 计算属性，store 一刷新即同步。

备选：整页手写 el-form——违反项目"schema 驱动表单"约定，否决。

### D6: 修改密码提交后主动登出并跳登录页

后端 bump token 版本号后旧 token 已废，前端继续停留会 401 弹窗。成功回调里 `ElMessage.success` → `authStore.logout()`（清 token/userInfo/codes）→ `router.push('/auth/login')`。确认密码一致性沿用现有 zod dependencies 校验。

### D7: 删除 security-setting.vue / notification-setting.vue

假数据 tab（密保手机/站内信开关）无后端支撑且无排期，直接删文件，index.vue 的 tabs 数组与插槽同步收缩为两项。Template demo 组件（web-antdv-next 与 @vben/common-ui 包内）不动。

### D8: API 层放置

- `changePasswordApi` 加到 `api/core/auth.ts`（与 login/logout 同属 auth 域）
- `updateProfileApi`（PATCH /user/profile）+ `getUserInfoApi` 若已存在于 `api/core/user.ts` 则原地扩展，否则新建 `api/core/user.ts`（现状：getUserInfoApi 从 `#/api` 导入，apply 时确认其所在文件）

## Risks / Trade-offs

- [改密 bump 版本号导致全端登出（含本会话）] → 属既有安全设计，前端主动引导重新登录，体验可预期
- [introduction 无长度上限可能超列宽] → 列 VARCHAR(255)，前端 Textarea maxlength=200 + 后端 DTO @Size(max=200) 双保险
- [MySQL 与 PostgreSQL schema 漂移] → 两文件同步加列，E2E 以 PostgreSQL 为准
- [删除 demo tab 后 @vben/common-ui 的 Profile 组件插槽契约变化] → 仅使用 v-if 按 tabsValue 渲染，不受 tabs 数量影响

## Migration Plan

单库 ALTER 幂等加列，重启后端即完成迁移；无数据回填。回滚 = 删列 + 还原前端，无数据损失风险。

## Open Questions

（无）
