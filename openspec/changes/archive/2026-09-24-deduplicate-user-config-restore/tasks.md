## 1. 实现
- [x] 1.1 为每个表格实例复用同用户的恢复结果，并在保存与账号切换后失效
- [x] 1.2 增加重复初始化、保存后读取的回归测试

## 2. 验证
- [x] 2.1 运行列配置回归测试与前端类型检查
- [x] 2.2 openspec validate deduplicate-user-config-restore --strict
- [x] 2.3 归档 openspec 变更（sync delta → archive）
