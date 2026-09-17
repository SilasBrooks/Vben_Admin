## 1. 存储抽象与配置

- [x] 1.1 `application.yml`：`spring.servlet.multipart`（max-file-size 10MB / max-request-size 15MB）+ `vben.file.storage: local` + `vben.file.local.base-path: ./files`；根 .gitignore 加 `files/`
- [x] 1.2 新建 `common/storage/`：`StorageService` 接口 + `StoredFile`（key/size）+ `StorageProperties` + `LocalStorageService`（`@ConditionalOnProperty`，日期分桶 + UUID 文件名，store/open/delete，路径穿越防护：key 白名单字符校验）
- [x] 1.3 `schema-postgres.sql`/`schema-mysql.sql` 幂等新增 `sys_file` 表与 `sys_user.avatar` 列；`mvn -q compile` 通过

## 2. 文件模块（后端）

- [x] 2.1 新建 `module/system/entity/SysFile` + Mapper + `SysFileService`：上传校验（扩展名白名单/大小）→ store → 落库 → 返回 `FileItem`（id/originalName/url）；删除 = deleteById + storage.delete（物理不存在时容错）
- [x] 2.2 新建 `FileController`：`POST /file/upload`（`System:File:Upload`，`@RateLimit`）、`GET /file/list`（`System:File:List`，originalName 模糊 + 分页）、`DELETE /file/{id}`（`System:File:Delete`）、`GET /file/{id}/content`（登录即可；图片 inline、其余 attachment；404 容错）；springdoc @Tag「文件管理」
- [x] 2.3 `POST /file/avatar`：登录用户上传头像（仅图片白名单，≤5MB，无需权限码）→ 更新本人 `sys_user.avatar` = 文件 id；用户信息接口（LoginResult 与 profile 数据源）返回 avatar
- [x] 2.4 `DatabaseSeeder`：系统管理目录下「文件管理」菜单（`/system/file`，序 6——任务原定序 5 已被「数据字典」占用，顺延保证排序正确）+ `System:File:List/Upload/Delete` 三权限码入 systemSet

## 3. 前端

- [x] 3.1 新建 `api/system/file.ts`（上传/列表/删除/头像 + FileItem 类型；content 用 responseType blob）
- [x] 3.2 新建 `views/system/file/index.vue`：useVbenVxeGrid 列表（原始名/大小格式化/类型/上传人/时间/操作）+ originalName 搜索 + 上传按钮（ElUpload 或手动 FormData，`v-access:code="'System:File:Upload'"`）+ 图片预览（blob → objectURL 弹层）+ 删除（确认框，`v-access:code="'System:File:Delete'"`）；`pnpm --filter @vben/web-ele exec vue-tsc --noEmit` 通过
- [x] 3.3 用户信息类型/展示补 avatar（可展示处接入；profile 共享组件不支持头像位则记录说明）

## 4. 验证与收尾

- [x] 4.1 curl E2E（XFF 隔离）：png 上传成功→列表可见→content 200 且 Content-Type 正确→白名单外 .exe 400→超 10MB 被拒→删除后 content 404→未登录 content 401→无 Delete 权限账号 403；avatar 上传后 userinfo 返回 avatar
- [x] 4.2 浏览器冒烟：文件管理页列表/上传/预览/删除全流程；受限账号按钮隐藏
- [x] 4.3 `openspec validate add-file-storage --strict` 通过；README 功能清单/目录结构更新；归档 + commit + push（gy, gy:master）+ 更新记忆
