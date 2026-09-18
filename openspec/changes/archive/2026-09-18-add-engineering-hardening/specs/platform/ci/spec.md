# platform/ci Delta

## ADDED Requirements

### Requirement: 持续集成流水线

系统 SHALL 提供 GitHub Actions CI：push 与 pull request 触发两个独立 job——后端执行单元测试（Maven test，Java 21），前端执行构建（pnpm install + 构建 web-ele 应用）；任一 job 失败 MUST 将该次运行标记为失败。后端测试 MUST 不依赖外部容器（单测 Mock 外部依赖，可在 CI 裸环境通过）。

#### Scenario: push 触发后端与前端检查

- **WHEN** 向远端推送一次包含后端与前端代码的提交
- **THEN** CI 同时启动 backend 与 frontend job，均完成时标记成功

#### Scenario: 测试失败阻塞

- **WHEN** 某次推送引入一个失败的单元测试
- **THEN** backend job 失败，该次 CI 运行整体标记失败
