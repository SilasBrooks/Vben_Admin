# 设计：add-login-captcha-ratelimit

## 关键决策

### D1：验证码校验顺序 = 限流 → 锁定 → 验证码 → 密码

限流最便宜（挡流量）；锁定次之（已认定恶意）；验证码在密码前（防密码爆破需先挡脚本）。验证码失败不计入账号锁定（防手误误锁），由限流兜底。

### D2：弹窗内替换拼图 → 服务端图形码

保留现有"表单 → 弹窗 → 确认登录"的交互骨架，把 SliderTranslateCaptcha 换成「后端图片 + 输入框 + 点击刷新」。输码错误时保持弹窗打开并自动换图，登录成功才关闭——比整页表单加验证码字段改动更小、体验一致。

### D3：内存态 + 显式单实例语义

验证码、失败计数、限流窗口全部存 ConcurrentHashMap（惰性过期清理，验证码池上限 1 万条）。本项目无 Redis；多实例部署时需替换为集中存储——代码中以类注释 + spec 场景明确标注该边界。

### D4：AWT 绘制，零新依赖

BufferedImage + Graphics2D（4 字符、随机旋转、干扰线），headless 安全。不引入 kaptcha 等陈旧依赖；字符集剔除 0O1lI 等易混字符。

### D5：dev 回显便于联调

`vben.captcha.echo-enabled`（默认 false，仅 application-dev.yml 开启）时响应附带 `devCode` 明文，供 curl/联调用；生产配置不开启。属显式声明的能力，非魔法后门。

### D6：@RateLimit 通用注解

`@RateLimit(name, limit, windowSeconds, scope=IP|USER)` + AOP 切面，按 scope 取 IP 或登录用户 id 组 key。IP 取 X-Forwarded-For 首段（兼容 nginx 反代）。AI 对话接口按 USER 限流，认证类按 IP 限流。
