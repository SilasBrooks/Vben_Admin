# 变更提案：add-login-captcha-ratelimit

## Why

登录接口当前在裸奔（roadmap 1.2，自 2026-09-11 挂账）：登录页的拼图验证码是纯前端组件，**后端完全不校验任何验证码**，`/api/auth/login` 可被脚本无限爆破；无失败锁定、无限流。作为要公开展示/复用的底座，这是第一优先级的安全洞。

## What Changes

- **服务端图形验证码**：`GET /api/auth/captcha` 生成 4 位图形码（AWT 绘制，base64 PNG，2 分钟有效、一次性使用）；登录请求必须携带 captchaId + captchaCode，服务端先验码再验密码
- **登录页改造**：现有"拼图弹窗"替换为服务端验证码弹窗（同一交互形态：表单 → 弹窗出图输码 → 确认登录），输错不关弹窗、可点击刷新
- **失败锁定**：同一用户名或同一 IP，15 分钟内失败 ≥5 次 → 锁定 15 分钟（429 + 剩余时间）；登录成功清零
- **接口限流**：新增可复用 `@RateLimit` 注解（AOP 固定窗口，按 IP），应用于 login（10 次/分）、captcha（30 次/分）、AI 对话（10 次/分/用户）

## Capabilities

### New Capabilities

- `security/login-guard`: 服务端验证码、失败锁定、登录/AI 接口限流

## Impact

- 后端：新增 CaptchaService / LoginAttemptService / RateLimit 切面（约 250 行，零新依赖，纯 JDK AWT）；AuthController 登录流程插入三道检查；JwtAuthFilter 白名单加 `/auth/captcha`
- 前端：仅 login.vue + api/auth.ts（弹窗内容替换、登录参数加两个字段）
- 状态全部在内存（单实例语义），多实例部署需换 Redis——在 design 中明确记录
