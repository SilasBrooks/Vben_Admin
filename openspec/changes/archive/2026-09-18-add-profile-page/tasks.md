# add-profile-page 实施任务

## 1. 后端：introduction 列与本人资料接口

- [x] 1.1 `SysUser` 实体加 `introduction` 字段；`db/schema-postgres.sql`（ADD COLUMN IF NOT EXISTS + COMMENT）与 `db/schema-mysql.sql`（information_schema 判断式）同步加 `introduction VARCHAR(255) NULL`，重启后端确认列已建且无报错
- [x] 1.2 `UserController` 新增 `PATCH /user/profile`：`UpdateProfileDto`（nickname @NotBlank + @Size(max=200)、introduction 可空 @Size(max=200)），登录即可无权限码，仅改本人 nickname/introduction，复用 MetaObjectHandler 自动填 updateTime；curl 断言：改昵称/简介 200 且 /user/info 回读一致，nickname 空串 400，无 token 401
- [x] 1.3 `GET /user/info` 返回补充 `introduction`（null 兜底空串）；curl 断言响应含 introduction 字段

## 2. 前端：路由与 API

- [x] 2.1 `api/core/auth.ts` 补 `changePasswordApi(oldPassword, newPassword)`；确认 `getUserInfoApi` 所在文件后新增 `updateProfileApi(nickname, introduction)`（PATCH /user/profile），`pnpm` 类型检查通过
- [x] 2.2 `router/routes/core.ts` 注册 `Profile` 路由（BasicLayout 下、hideInMenu、登录即可），验证：dev 启动后头部下拉点「个人中心」不再 404 且能进入页面

## 3. 前端：重写个人中心页面

- [x] 3.1 重写 `base-setting.vue`：头像展示 + 点击上传（uploadAvatarApi，accept=image/*，成功后刷新 userStore 使 header 同步）、昵称/简介可编辑提交 updateProfileApi、用户名/角色只读展示；删除 mock 角色与假字段；浏览器验证：上传新头像后页面与 header 即时变化，改昵称保存后回读一致
- [x] 3.2 重写 `password-setting.vue`：提交调 changePasswordApi，成功后 ElMessage 提示「密码已修改，请重新登录」→ 登出 → 跳登录页；浏览器验证：旧密码错误提示 400 文案且不掉线，改密成功后自动到登录页且旧 token 请求 401，用新密码可重新登录
- [x] 3.3 删除 `security-setting.vue`、`notification-setting.vue`，`index.vue` tabs 收缩为「基本设置」「修改密码」两项；`vue-tsc --noEmit` 通过且页面无残留引用报错

## 4. 收尾验证与文档

- [x] 4.1 E2E 串测：vben/123456 登录 → 个人中心改昵称+简介+头像 → 刷新页面回读一致 → 改密 → 重登 → 头像/昵称在 header 正常显示；受限账号 stockAdmin 同样可改本人资料（无权限码要求）
- [x] 4.2 README 功能清单补一行「个人中心（头像/昵称/简介/改密）」，核对注意事项无需变更
- [ ] 4.3 `openspec validate --strict` 通过、归档变更、commit/push（gy + master）
