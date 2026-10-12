# Design: kb-large-file-pipeline

## Context

知识库现状：单请求上传（≤10MB，md/txt），正文直接入 `kb_doc.content`（DB text），受理后异步切块向量化（状态 处理中/完成/失败），无进度、无断点、无重试。存储层 `StorageService` 已可插拔（local/minio），文件上传安全约束（白名单/限流/UUID）已有先例。用户要求支撑几 MB～几十 MB 级知识库文件（报告/手册 PDF/Word）。

## Goals / Non-Goals

**Goals**
- 200MB 级文件可靠上传：分片协议、断点续传、秒传、完整性校验
- PDF/DOCX 文本提取（流式，内存常数）
- 管道可观测（阶段+进度）、可恢复（断点续跑+重试）、可保护（chunk 配额）
- 存量数据与 ≤10MB 原路径完全兼容

**Non-Goals**
- 不做通用文件分片服务（仅 KB 文档专用，技能包/头像维持现状）
- 不做分布式任务队列/多实例抢占（单机后台执行器即可，见 D6）
- 不做 OCR（扫描件直接报错）、不做 Office 老格式 .doc（仅 .docx）
- 不做 MinIO 分片直传（presigned multipart），应用层合并已满足 200MB（见 D4）

## Decisions

### D1. 双路径上传，按大小选路
≤10MB 保持现有 `POST /system/kb/doc` 单请求（语义与存量数据完全不动）；>10MB 走分片协议。前端按 `file.size` 自动选路。理由：兼容成本最低，小文件没必要为统一协议付出 SHA-256 预计算（200MB 哈希计算数秒，小文件是纯开销）。

### D2. 新增两表 + kb_doc 加列（upgrade SQL 幂等）
- `kb_file`：`sha256`（唯一）、`size`、`storage_key`、`ref_count`、`create_time`——文件物理实体去重载体，秒传命中即查此表
- `kb_upload_session`：`upload_id`（唯一）、`sha256`、`file_name`、`file_size`、`chunk_size`、`total_chunks`、`received_chunks`（jsonb int 数组）、`status`、`user_id`、`expire_at`
- `kb_doc` 加列：`file_id`（关联 kb_file，NULL=旧路径正文入库）、`stage`（parsing/chunking/embedding，完成置 NULL）、`progress`（0-100）、`processed_chunks`（embedding 断点）、`total_chunks`
- 删除文档：`ref_count` 递减，归零删存储文件（best-effort，失败告警）

### D3. 分片会话安全与清理
- uploadId 服务端 UUID 生成；分片临时目录 `files/kb-chunks/{uploadId}/`（local 存储 gitignore 覆盖；minio 部署时临时分片仍走本地盘，合并后进对象存储——分片是短暂暂存，不值得对象化）
- 会话归属校验：chunk/complete/abort 校验会话 user_id 与当前用户一致
- 单用户进行中会话上限 5；过期 24h 由 `@Scheduled` 每小时清理（删目录+删记录）
- complete 合并：顺序打开分片流，`InputStream.transferTo` 链式写目标流，同时用 `DigestInputStream` 计算合并后 SHA-256，不匹配即失败清理；合并全程不经堆内全量字节

### D4. 分片协议端点（均 Kb:Doc:Upload + @RateLimit）
`POST /system/kb/doc/upload/init`（json）/ `POST /system/kb/doc/upload/{uploadId}/chunk?index=`（multipart bin）/ `POST /system/kb/doc/upload/{uploadId}/complete` / `DELETE /system/kb/doc/upload/{uploadId}`。分片大小服务端约束 1-10MB（默认 5MB），total_chunks ≤ 40（200MB/5MB）。init 幂等：`(user_id, sha256, file_size)` 命中进行中会话直接返回其 uploadId+已收列表；秒传：sha256 命中 `kb_file` 且有有效引用 → 直接建文档返回 `instant=true`。

### D5. 解析器抽象 DocumentTextExtractor
接口 `extract(InputStream) → 文本迭代/分段流`，按扩展名路由：md/txt 直接 UTF-8 读；pdf 用 PDFBox 3 `Loader.loadPDF(RandomAccessReadBufferedFile)` 按页 `getText`（每页处理完即释放，内存常数）；docx 用 POI `XWPFDocument` 按段落遍历。解析结果为空 → failed「未识别到文本内容（可能是扫描件）」。依赖：`org.apache.pdfbox:pdfbox` + `org.apache.poi:poi-ooxml`（Apache-2.0，与现有依赖无冲突）。解析产物仍以纯文本交给现有切块器——Markdown 标题切分仅对 .md 生效，pdf/docx 提取文本按长度窗口切分。

### D6. 管道执行器：单机阻塞队列 + 启动恢复
`KbPipelineExecutor`：单线程 `ThreadPoolTaskExecutor`，任务=文档 id，按批（每批 N 块，批间可限速）调 embedding 并 `processed_chunks` 落库。`@Scheduled` 每 30s 扫 `status=processing` 且不在执行中的文档（服务重启遗留）置 failed（原因「服务重启导致处理中断，可重试」）。重试接口 `POST /system/kb/doc/{id}/retry`（Kb:Doc:Upload 权限）：failed → processing 重新入队，从 `processed_chunks` 续跑（parsing/chunking 阶段产物已在库，直接跳到 embedding）。进度公式：parsing 0-20%，chunking 20-40%，embedding 40-100%（按 processed/total 线性）。chunk 配额 20000（超限 failed 提示拆分）。

### D7. 进度反馈用轮询而非 WebSocket
列表页已存在轮询刷新习惯（vxe-grid 手动刷新），阶段+进度放进现有列表响应体即可，前端对存在处理中文档的页面按 3s 轮询，全部完成后停止。不引入 WS 推送复杂度。

## Risks / Trade-offs

- **SHA-256 前端计算阻塞**：200MB 全文件哈希在主线程数秒——用 `crypto.subtle` 分块喂入 + 上传前提示「校验文件中」；秒传/续传收益远大于开销
- **poi-ooxml 体积大**（~20MB jar）：仅 docx 用；可接受，部署包已含 pgvector 驱动等重依赖
- **大 PDF 提取文本质量**：双栏/表格顺序错乱属 PDFBox 已知特性，MVP 接受；提示用户复杂版式建议转 md 上传
- **合并时磁盘峰值**：临时分片 + 合并产物 ≈ 2×文件大小，200MB 上限下可控；complete 成功即删分片目录
- **单机执行器无横向扩展**：文档级串行足够 MVP；任务以 DB 行为唯一事实，未来换 MQ/抢锁不动数据模型
- **jsonb received_chunks 并发写**：分片并发到达时按「读-合并-写」可能丢并发更新 → chunk 落盘成功即用 SQL `jsonb ||` 原子追加索引，避免读改写竞争

## Migration

1. `service/src/main/resources/db/schema-postgres.sql` 同步新表/新列
2. 新增 `db/upgrade-20261010-kb-large-file-postgres.sql`（幂等：加列 + 建两表 + COMMENT），存量库 vben5 手工执行；存量 kb_doc `file_id=NULL` 语义即「正文入库」，无需数据迁移
3. README「知识库」段落同步 + 存量库清单补 upgrade SQL
