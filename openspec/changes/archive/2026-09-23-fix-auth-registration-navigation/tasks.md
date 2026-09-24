## 1. 实现

- [x] 1.1 修复登录页过渡根节点及缓存生命周期
- [x] 1.2 将注册占位表单改为账号创建说明，补齐中英双语
- [x] 1.3 同步 README 的认证行为说明

## 2. 验证与归档

- [x] 2.1 运行前端类型检查并验证真实浏览器往返导航、表单重置与中英显示
- [x] 2.2 执行 openspec validate fix-auth-registration-navigation --strict
- [x] 2.3 归档 openspec 变更（sync delta → archive）

## 验证记录

- `pnpm check:type`：通过，web-ele 的 vue-tsc 完成。
- 浏览器：修复前真实复现登录 → 注册后仅剩页头和页脚；修复后多次往返、按钮返回与浏览器后退均正常。
- 表单：先触发必填错误并输入测试密码，返回后校验提示消失且密码为空；勾选记住账号后，返回保留测试用户名、密码为空、验证码弹窗关闭。测试用户名已通过取消记住账号清除，未实际提交登录。
- 语言：通过真实语言菜单切换英文，说明和返回按钮显示英文；已恢复中文。
- `openspec validate fix-auth-registration-navigation --strict`：通过。
- 新标签页直接访问账号说明并返回登录：正常，无控制台错误；仅见既有 StorageManager 空前缀警告。
- `openspec archive fix-auth-registration-navigation --yes`：已同步 `security/login-guard` 主规格并归档。
