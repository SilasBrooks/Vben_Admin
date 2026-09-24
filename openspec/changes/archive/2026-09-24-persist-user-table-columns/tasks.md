## 1. 实现
- [x] 1.1 新增个人配置表、接口、输入校验与用户隔离
- [x] 1.2 扩展可选幂等业务键与执行期间防重
- [x] 1.3 统一接入十个可自定义列表的挂载恢复、保存、重置与稳定列标识
- [x] 1.4 更新 README 与数据库升级脚本

## 2. 验证
- [x] 2.1 验证列配置转换、快速保存顺序、失败恢复及用户隔离
- [x] 2.2 运行前端类型检查、后端编译和测试，完成可用环境的集成验证
- [x] 2.3 openspec validate persist-user-table-columns --strict
- [x] 2.4 归档 openspec 变更（sync delta → archive）

## 验证记录

- `pnpm check:type` 通过；`pnpm build:ele` 通过。
- 前端列配置 Vitest：5 项通过，覆盖配置转换与非法项处理、顺序保存、读取等待、失败重试、重置、切换账号。
- `mvn -q '-Dmaven.compiler.proc=full' compile test` 通过。本机为 JDK 25，需显式开启 Lombok 注解处理；项目目标仍为 Java 21。
- `USER_CONFIG_INTEGRATION_TEST=true` 下执行 `mvn -q '-Dmaven.compiler.proc=full' '-DargLine=-Djdk.net.unixdomain.tmpdir=C:/Windows/Temp' '-Dtest=UserConfigIntegrationTest' test` 通过。短临时目录用于本机 Windows/JDK 25 的域套接字兼容；测试使用随机 HTTP 端口、真实 PostgreSQL/Redis，验证鉴权、跨用户/表隔离、连续保存、数据库唯一行、重置与英文校验提示。个人配置表已创建，测试用户和配置已清理。
- 后端测试报告合计 108 项：103 项通过、5 项其他既有可选集成测试跳过，无失败。
- 运行环境修复：确认正在运行的 5777 Vite 使用本地 `data/vite.dev.config.mjs`，实际代理目标为 18080。原 18080 后端启动早于新增 Controller，导致 `/api/user-config` 返回 404；使用原 Java 21 和邮箱启动脚本重启本项目后端后，新接口已加载。
- 通过实际 `http://localhost:5777/api` 代理，使用独立临时账号验证 `GET /user-config?key=table.system.user` 与 `POST /user-config/save` 均返回 200，保存后读取、连续保存与空数组重置均通过，测试数据和会话已清理。未完成交互式浏览器列表验证。
