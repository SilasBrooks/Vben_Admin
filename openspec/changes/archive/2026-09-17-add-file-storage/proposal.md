## Why

管理系统普遍需要文件能力（头像、附件、导入模板），当前项目完全没有：无上传接口、无存储抽象、无文件记录。直接堆一个上传接口会把存储实现写死在业务里，因此本次同时建立可插拔存储抽象（本地实现起步，预留 OSS/MinIO），并配套文件管理页与头像接入，形成完整闭环。

## What Changes

- **存储抽象**：新增 `StorageService` 接口（store/open/delete），默认 `LocalStorageService` 实现（日期分桶 + UUID 随机文件名，保留扩展名）；配置项 `vben.file.storage` 选择实现，为后续 OSS/MinIO 留位
- **上传安全**：扩展名白名单（图片/文档/压缩包）、单文件大小上限（默认 10MB，`spring.servlet.multipart` 同步约束）、UUID 随机存储名（防路径穿越与文件名猜测）、按登录用户记录上传人
- **文件记录**：新增 `sys_file` 表（原始名/存储键/大小/类型/上传人/业务类型/时间），上传落库、删除时记录与物理文件同步移除
- **接口**：上传 `POST /file/upload`、分页列表 `GET /file/list`、删除 `DELETE /file/{id}`、下载/预览 `GET /file/{id}/content`（流式输出，图片内联、附件 attachment）；下载需登录，预览由前端 blob + objectURL 实现（不开放匿名静态映射）
- **权限码**：`System:File:List` / `System:File:Upload` / `System:File:Delete`；菜单 seeder 注册「文件管理」页（系统管理目录下）
- **头像接入**：`sys_user` 增加 `avatar` 列；已登录用户可上传头像（本人操作，无需权限码）并更新本人 avatar；用户信息接口返回 avatar

## Capabilities

### New Capabilities

- `platform/file-storage`: 文件上传/下载/删除与记录管理——存储抽象可插拔，上传受白名单与大小约束，记录与物理文件生命周期一致，文件管理页与头像场景接入

## Impact

- **后端**：`schema-postgres.sql`/`schema-mysql.sql`（sys_file 表 + sys_user.avatar 列，幂等）、`application.yml`（multipart 限制 + vben.file 配置）、新增 `common/storage/`（StorageService、LocalStorageService、StorageProperties）、新增 `module/system/`（SysFile entity/mapper/service、FileController）、`SysUser` 实体加 avatar、用户信息接口补 avatar、`DatabaseSeeder`（菜单 + 3 权限码）
- **前端**：新增 `api/system/file.ts` 与 `views/system/file/index.vue`（vxe-table 列表 + 上传按钮 + 图片 blob 预览 + 删除，v-access 权限码）；用户信息类型补 avatar 字段
- **部署**：本地存储目录默认 `./files`（需加入根 .gitignore）；无新增中间件依赖
- **兼容性**：纯新增能力，无破坏性变更；API 文档自动收录「文件管理」分组
