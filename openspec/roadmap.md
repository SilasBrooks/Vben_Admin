# 通用后台模板 功能规划路线图

> 依据 2026-08-28 盘点：已完成 登录认证(JWT) / 用户 / 角色 / 菜单 / 部门 / 按钮级 RBAC / 动态路由 / 库存业务模块。
> 本文档是需求池与优先级排序，每个功能开工前仍需按 OpenSpec 流程立变更（proposal → design → specs → tasks）。
> 主规格库：`openspec/specs/`（已归档能力：system/dept、monitor/oper-log、monitor/login-log、system/dict）。

## P1 审计与安全底座（最高优先级）

### 1.1 操作日志 + 登录日志
- 后端：`sys_oper_log` / `sys_login_log` 表；`@Log` 注解 + AOP 自动记录操作人/接口/入参/结果/耗时；登录成功与失败均记录 IP、UA
- 前端：`views/monitor/` 下两个列表页（分页 + 条件过滤 + 清空）
- 权限码：`Monitor:OperLog:List/Delete`、`Monitor:LoginLog:List/Delete`
- 依赖：无

### 1.2 登录验证码 + 接口限流
- 图形验证码（前端 canvas 或后端生成）；登录失败次数限制（同账号 + 同 IP 双维度）
- 依赖：无

### 1.3 接口文档
- 引入 `springdoc-openapi`，Swagger UI 挂 `/api/swagger-ui`；生产环境关闭
- 依赖：无

## P2 配置能力

### 2.1 数据字典 ✅（2026-09-02 归档：add-dict-management）
- `sys_dict_type` / `sys_dict_data` 两表 + 管理页；前端提供 `useDict()` hook，下拉选项从字典取值
- 优先替换硬编码下拉：库存模块、用户状态等（库存状态示例已接入）
- 依赖：无

### 2.2 系统参数配置
- `sys_config`（key-value，含是否内置标记）；管理页 + 后端缓存
- 依赖：无

### 2.3 文件上传
- 本地磁盘存储起步：`/upload` 目录 + `sys_file` 记录表；统一上传接口 + 通用上传组件
- 头像、附件复用
- 依赖：无

## P3 权限深化（依赖部门管理，已铺好路）

### 3.1 数据权限 ✅（已归档 add-data-permission-scope）
- 角色上增加 `dataScope` 字段：全部 / 自定义部门 / 本部门 / 本部门及下级 / 仅本人
- AOP 切面（`@DataScope` + `DataScopeAspect`）解析可见部门集合放入 ThreadLocal，业务查询按需追加过滤；用户列表已接入，库存模块跟进
- 依赖：system/dept ✅（已归档）

### 3.2 部门级角色授权（可选扩展）
- 支持把角色授权到部门维度（部门管理页维护「部门角色」），作为数据权限「自定义部门」的配置入口
- 依赖：3.1

### 3.3 个人中心
- 用户自助：改昵称/头像、改密码（校验旧密码）；右上角下拉入口
- 依赖：2.3（头像上传）

## P4 体验增强

### 4.1 首页工作台
- 统计卡片（用户数/今日登录/库存总量等）+ ECharts 图表；按角色可见性裁剪
- 依赖：1.1（登录日志提供今日登录数据）

### 4.2 通知公告
- `sys_notice` + 管理页 + 首页公告栏；后期可升级站内信
- 依赖：无

### 4.3 AI 智能助手 ✅（2026-09-14 归档：add-ai-assistant）
- 右下角悬浮入口（logo.png 圆形按钮）→ 聊天面板（SSE 流式打字机输出）
- DeepSeek 大模型 + Function Calling：查询类工具自动执行、新增类工具确认后执行
- 6 个工具：查询/新增 用户、角色、部门
- 权限：工具执行按当前登录用户权限码校验；API Key 仅存后端
- 依赖：无

## P5 生产化（上线前必须）

### 5.1 数据库迁移 H2 → PostgreSQL
- 已有 `schema-mysql.sql` 基础，补 `schema-postgres.sql`；配置 profile 切换
### 5.2 Redis 引入
- 验证码/字典/参数缓存、登录限流计数
### 5.3 Docker 部署
- 后端 Dockerfile + 前端 nginx 镜像 + docker-compose 一键起

## 后置（SaaS 阶段）

- 多租户（tenant_id 全表隔离 + 租户套餐）、工作流（Flowable/Warm-Flow 审批中心）、RAG 知识库（AI 助手升级）

## 建议开工顺序

~~1.1 操作/登录日志 → 2.1 字典 → 3.1 数据权限 → 1.2 验证码限流 → 2.3 文件上传 → 3.3 个人中心 → 4.1 工作台 → P5 生产化~~

已完成：1.1 日志 → 2.1 字典 → 3.1 数据权限 → 4.3 AI 助手
待办：1.2 验证码限流 → 2.3 文件上传 → 3.3 个人中心 → 4.1 工作台 → P5 生产化
