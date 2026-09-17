## Context

后端单模块 Spring Boot 3.5 + MyBatis-Plus，schema SQL 幂等（IF NOT EXISTS / 幂等列添加）。权限体系：`@RequirePermission` 后端兜底 + 前端 `v-access:code`；菜单种子在 `DatabaseSeeder`（仅空库触发，重置需 TRUNCATE sys_* 后重启）。前端仿 login-log/online 的 vxe-table 页面模式。已接入 Redis/限流（`@RateLimit` 可直接复用）。用户信息接口现返回 id/username/realName/roles/homePath（LoginResult）与 profile 页 `getUserInfoApi`（`_core/profile`），无 avatar 字段。

## Goals / Non-Goals

**Goals:**

- 一套可复用的上传/下载/删除闭环 + 管理页，头像作为第一个业务接入
- 存储实现与业务解耦：业务只依赖 `StorageService` 接口与 `StoredFile` 结果
- 上传安全三件套：白名单、大小上限、随机存储名

**Non-Goals:**

- OSS/MinIO 的具体实现（仅留接口与配置位，不引入依赖）
- 分片/断点续传、秒传、图片压缩裁剪
- 文件秒级签名 URL、防盗链（README 注明生产建议）
- 回收站/软删除（删除即物理移除 + 记录移除）

## Decisions

- **存储抽象**：`StorageService`（`StoredFile store(InputStream, fileName, contentType)` / `InputStream open(key)` / `void delete(key)`）+ `LocalStorageService` 实现；`vben.file.storage`（默认 `local`）+ `@ConditionalOnProperty` 绑定实现 bean，未来 oss 实现加条件即插
- **本地布局**：`vben.file.local.base-path`（默认 `./files`）+ `yyyy/MM/dd/{uuid}.{ext}` 日期分桶，storage_key 即相对键；目录加入根 .gitignore
- **流式接口而非静态映射**：`GET /file/{id}/content` 由控制器按 sys_file 记录输出（带登录校验与正确 Content-Type/Content-Disposition），不开放匿名静态资源映射——避免 `<img src>` 直链绕过认证；前端预览用 axios blob → objectURL
- **下载白名单**：不加入 JwtAuthFilter 白名单（需登录）；未认证请求 401 由统一结构返回
- **Multipart 配置**：`spring.servlet.multipart.max-file-size: 10MB`、`max-request-size: 15MB`；应用层再按业务上限校验（头像 5MB、普通 10MB）
- **类型校验**：以扩展名白名单为主（模板项目语义），响应头 Content-Type 取记录值（上传时从原始名推导，非客户端可控字段），防 Content-Type 欺骗直投 HTML
- **sys_file 表**：`id, original_name, storage_key(unique), size, content_type, biz_type(avatar/general), uploader_id, create_time`；MyBatis-Plus MetaObjectHandler 自动填充时间
- **头像链路**：`sys_user.avatar` 列存文件 id；`POST /file/avatar`（登录即可，内部走白名单仅图片 + 5MB）→ 更新本人 avatar；用户信息接口（LoginResult 与 profile 用 getUserInfoApi 的数据源）补 `avatar` 字段；前端展示接在 userinfo 处，profile 编辑组件若不支持头像位则仅打通数据链路并展示于文件页说明（避免改 common-ui 共享组件）
- **菜单种子**：系统管理目录下「文件管理」`/system/file`（icon `ant-design:file-outlined`，序 5）+ 三个 F 权限码，入 systemSet

## Risks / Trade-offs

- [扩展名校验不校验文件魔数，伪装文件可上传] → 模板项目可接受；存储名随机 + Content-Type 固定 + 不提供执行上下文（Nginx 部署时 files 目录禁 php/exe 执行），README 注意事项注明
- [下载接口需登录但流式输出占连接] → 当前规模无虞；大文件场景应转签名直链（Non-Goal 注明）
- [删除是物理删除，误删不可恢复] → 文件管理页删除需 ElMessageBox 确认；软删除列入 Non-Goal
- [TRUNCATE 重置种子时 sys_file 残留孤儿文件] → seeder 不清文件表，README 不承诺；孤儿文件无引用不暴露

## Migration Plan

1. schema SQL 幂等重建（sys_file + avatar 列）→ 2. 后端新增存储模块与接口 → 3. seeder 菜单重置生效 → 4. 前端页面 + 头像链路 → 5. 回滚：还原代码即可，files 目录与 sys_file 表留存无害

## Open Questions

- 无
