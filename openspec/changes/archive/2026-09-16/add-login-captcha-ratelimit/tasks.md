# 任务清单：add-login-captcha-ratelimit

- [x] 1.1 后端 `CaptchaService`：AWT 绘制 4 位图形码（剔除易混字符、干扰线）、一次性校验、2 分钟 TTL、池上限惰性清理；`vben.captcha.echo-enabled` dev 回显
- [x] 1.2 后端 `LoginAttemptService`：用户名+IP 双维度 15 分钟窗口失败计数、达 5 次锁 15 分钟（返回剩余秒数）、成功清零
- [x] 1.3 后端 `@RateLimit` 注解 + AOP 切面（IP/USER scope、固定窗口、X-Forwarded-For 兼容）+ BizException.tooManyRequests(429)
- [x] 1.4 后端 AuthController：GET /auth/captcha 端点；login 注入三道检查（注解限流 → 锁定 → 验证码 → 密码）；LoginRequest 加 captchaId/captchaCode 必填校验；JwtAuthFilter 白名单加 /auth/captcha
- [x] 1.5 AI 对话接口挂 @RateLimit(USER, 10/min)
- [x] 1.6 前端：api/auth.ts 加 getCaptchaApi 与登录参数；login.vue 拼图弹窗替换为服务端验证码弹窗（图片+输入框+点击刷新、错误保持弹窗）
- [x] 1.7 mvn compile + vue-tsc 通过
- [x] 2.1 curl E2E：无验证码 400 / 错码 400 / dev 回显正确码登录成功且验证码一次性 / 5 次失败锁定 429 / login 与 captcha 限流 429 / AI 对话限流 429
- [x] 2.2 浏览器冒烟：登录页弹窗出图输码 → 登录进系统
- [x] 2.3 同步主 spec（security/login-guard）→ 归档 → README 功能清单更新
