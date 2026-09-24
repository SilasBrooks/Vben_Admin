## 1. 实现

- [x] 1.1 用户配置读取支持只读 menu，删除旧菜单接口并同步双语错误文案
- [x] 1.2 前端路由初始化改用用户配置 API，更新现行文档
- [x] 1.3 补充菜单权限隔离、空菜单、只读 key 与认证回归测试

## 2. 验证

- [x] 2.1 运行后端编译和测试、前端类型检查，验证菜单与原个人配置接口
- [x] 2.2 openspec validate merge-menu-into-user-config --strict
- [x] 2.3 归档 openspec 变更（sync delta → archive）

## 验证记录

- Java 21 下 `mvn -q clean compile test` 通过；clean 确保已删除的旧 Controller 编译产物不再注册接口。
- `USER_CONFIG_INTEGRATION_TEST=true` 下 `mvn -q '-DargLine=-Djdk.net.unixdomain.tmpdir=C:/Windows/Temp' '-Dtest=UserConfigIntegrationTest' test` 通过。使用真实 PostgreSQL、Redis 和随机端口 HTTP，验证权限菜单树、禁用菜单与按钮过滤、跨用户隔离、角色停用后更新、同名个人配置无法覆盖菜单、只读英文提示、旧接口 404 及原列表配置读写；临时用户、角色、菜单和配置已清理。
- 合计测试报告 111 项：106 项通过、5 项既有可选集成测试跳过，失败和错误均为 0；用户配置单元测试 6 项、集成测试 2 项全部通过。
- `pnpm check:type` 通过。
- 已使用原 Java 21 和邮箱启动脚本重启本项目 18080 后端；通过 `http://localhost:5777` 验证 OpenAPI 已包含 `/user-config` 且不含 `/menu/all`，Vite 实际提供的路由初始化模块已调用 `getUserConfigApi('menu')`。
- `openspec validate merge-menu-into-user-config --strict` 通过。
