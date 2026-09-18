# platform/file-storage Delta

## ADDED Requirements

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
