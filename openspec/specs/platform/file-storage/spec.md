# platform/file-storage Specification

## Purpose
为系统提供统一的文件上传/存储能力：存储实现可插拔（本地起步，预留 OSS/MinIO），上传受安全约束，文件记录与物理文件生命周期一致，并为头像等业务场景提供接入点。

## Requirements

### Requirement: 文件上传

系统 SHALL 提供需认证的上传接口（`POST /file/upload`）：MUST 校验扩展名在白名单内（图片 jpg/jpeg/png/gif/webp、文档 pdf/doc/docx/xls/xlsx/ppt/pptx/txt、压缩包 zip），白名单外 MUST 拒绝（400）；单文件大小 MUST 受限（默认 10MB，超限拒绝）；存储名 MUST 为随机 UUID 保留原始扩展名（防路径穿越与猜测）；上传成功 MUST 写入 sys_file 记录（原始名、存储键、大小、类型、上传人、时间）并返回文件 id 与访问路径。上传接口 MUST 应用限流。

#### Scenario: 上传白名单内文件成功

- **WHEN** 已登录用户上传一张 2MB 的 png 图片
- **THEN** 返回文件 id，物理文件以随机名落盘，sys_file 出现对应记录（原始名/大小/类型/上传人）

#### Scenario: 白名单外类型被拒

- **WHEN** 用户上传 .exe 或无扩展名文件
- **THEN** 返回 400 明确提示不支持的类型，不落盘不落库

#### Scenario: 超大小限制被拒

- **WHEN** 用户上传超过 10MB 的文件
- **THEN** 请求被拒绝（413 或统一错误结构），不产生部分写入

### Requirement: 下载与预览

系统 SHALL 提供流式下载接口（`GET /file/{id}/content`），MUST 要求登录：按记录的实际 content_type 输出，图片类以内联方式呈现（前端 blob + objectURL 预览，不开放匿名静态映射），非图片以 attachment 下载并携带原始文件名；文件不存在或记录与物理文件不一致 MUST 返回 404。

#### Scenario: 图片预览

- **WHEN** 已登录用户在文件管理页点击图片文件的预览
- **THEN** 前端以 blob 拉取内容并弹层展示图片，未认证请求被 401 拒绝

#### Scenario: 记录删除后访问失效

- **WHEN** 某文件已被删除后再次请求其 content
- **THEN** 返回 404，物理文件也已移除

### Requirement: 文件管理页

系统 SHALL 提供文件分页列表（原始名/大小/类型/上传人/上传时间，原始名模糊过滤），删除操作 MUST 同时移除物理文件与记录；接口权限码：列表 `System:File:List`、上传 `System:File:Upload`、删除 `System:File:Delete`；前端操作按钮 MUST 使用 v-access:code 隐藏无权限操作。

#### Scenario: 有权限用户管理文件

- **WHEN** 具备 `System:File:List` 的管理员打开文件管理页
- **THEN** 列表展示所有文件记录，可按原始名过滤分页

#### Scenario: 无权限用户不可操作

- **WHEN** 不具备 `System:File:Delete` 的用户查看文件管理页
- **THEN** 删除按钮不渲染；直接调用删除接口返回 403

### Requirement: 头像接入

已登录用户 SHALL 可上传自己的头像（无需文件权限码，仅限图片类型）：成功后 MUST 更新本人 `sys_user.avatar`（存储文件 id 或访问路径），用户信息接口 MUST 返回 avatar 供前端展示；头像上传同受类型与大小约束。

#### Scenario: 上传并展示头像

- **WHEN** 用户在个人资料处上传一张图片作为头像
- **THEN** 本人 avatar 更新，刷新页面后用户信息中展示新头像

#### Scenario: 仅能改本人头像

- **WHEN** 用户调用头像接口
- **THEN** 仅更新自己的 avatar，无法指定他人

### Requirement: 存储实现可插拔

存储实现 SHALL 通过 `vben.file.storage` 配置选择：`local`（本地磁盘，默认）或 `minio`（MinIO 对象存储）。两种实现 MUST 保持相同语义与相同存储键格式（日期分桶 + 随机名 + 原始扩展名）：store 返回存储键与字节数；open 对不存在的键 MUST 抛出存储异常；delete MUST 幂等（对象不存在时不报错）。切换实现 MUST 不改变文件记录表结构与上传/下载接口契约。MinIO 连接参数（endpoint/ak/sk/bucket）MUST 由配置注入。

#### Scenario: 切换 MinIO 后上传下载一致

- **WHEN** 配置 `vben.file.storage=minio` 并提供可用 MinIO 服务后上传一个文件
- **THEN** 文件以日期分桶键写入指定 bucket，sys_file 记录生成，通过 content 接口可下载到相同内容

#### Scenario: MinIO 下删除幂等

- **WHEN** 同一文件在 MinIO 实现下被删除两次
- **THEN** 第一次删除成功，第二次静默完成不报错

#### Scenario: 默认仍为本地

- **WHEN** 不配置 vben.file.storage（或配置为 local）
- **THEN** 文件写入本地磁盘目录，不建立 MinIO 连接
