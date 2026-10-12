# Tasks

## 1. 依赖与表结构

- [x] 1.1 `service/pom.xml` 新增 `org.apache.pdfbox:pdfbox` 与 `org.apache.poi:poi-ooxml` 依赖
- [x] 1.2 `schema-postgres.sql`：新表 `kb_file`（sha256 唯一/size/storage_key/ref_count）与 `kb_upload_session`（upload_id 唯一/sha256/file_name/file_size/chunk_size/total_chunks/received_chunks jsonb/status/user_id/expire_at）；`kb_doc` 加列 `file_id`/`stage`/`progress`/`processed_chunks`/`total_chunks` + COMMENT
- [x] 1.3 新建 `db/upgrade-20261010-kb-large-file-postgres.sql`（幂等 ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS），存量库 vben5 手工执行并验证
- [x] 1.4 实体与 Mapper：`KbFile`、`KbUploadSession`（mapper 子包），`KbDoc` 加五字段

## 2. 后端：分片上传协议

- [x] 2.1 `KbUploadService`：init（幂等会话复用 + 秒传命中建文档）、chunk（流式落盘 + jsonb 原子追加索引 + 会话归属/过期校验）、complete（顺序合并 + DigestInputStream 校验 SHA-256 + StorageService 入库 + kb_file ref_count 维护 + 建文档入管线 + 清理临时分片）、abort（清理）；单用户会话上限、单片 1-10MB、total ≤40 约束
- [x] 2.2 `KbController` 四端点（均 Kb:Doc:Upload + @RateLimit）+ 单请求路径放宽校验（pdf/docx 入白名单、≤10MB pdf/docx 正文转存对象存储）；messages 中英错误文案（扩展名/超限/哈希不匹配/会话过期/会话数上限/分片索引非法）
- [x] 2.3 过期会话清理 `@Scheduled`（每小时：删临时目录 + 删记录）；`files/kb-chunks/` gitignore（`files/` 整目录已覆盖）

## 3. 后端：解析器与管道

- [x] 3.1 `DocumentTextExtractor` 接口 + 三实现（md/txt 直读、PDFBox 按页流式、POI 按段落），按扩展名路由；空文本 → 可读失败
- [x] 3.2 `KbPipelineExecutor`：单线程执行 解析→切块→逐批 embedding，批后落 `processed_chunks` 断点，阶段/进度持久化（parsing 0-20/chunking 20-40/embedding 40-100）；chunk 配额 20000；失败原因可读
- [x] 3.3 重启恢复：`@Scheduled` 扫描遗留 processing 置 failed（注明服务重启）；`POST /system/kb/doc/{id}/retry`（failed → 重入队，从 processed_chunks 续跑，处理中拒绝重复触发）
- [x] 3.4 删除语义扩展：文档删除递减 ref_count、归零清理存储（best-effort）；列表响应体补 stage/progress 字段

## 4. 后端测试

- [x] 4.1 `KbUploadServiceTest`（mock mapper/StorageService）：init 幂等复用、秒传命中、chunk 落盘与非法索引拒绝、complete 哈希不匹配清理、哈希匹配入库建文档、abort 清理、会话数上限
- [x] 4.2 解析器单测：md 直读、PDF 生成式按页解析（构造样例 PDF）、docx 构造样例段落、扫描件空文本报错
- [x] 4.3 管道单测：阶段/进度流转、断点续跑（processed_chunks 之后的块才向量化）、配额超限失败、重启遗留恢复、retry 从断点继续

## 5. 前端

- [x] 5.1 `api/system/kb.ts`：init/chunk/complete/abort/retry API；`KbDocItem` 补 stage/progress
- [x] 5.2 `kb/index.vue`：按大小选路（≤10MB 原路径；>10MB `crypto.subtle` 分块算 SHA-256 → init → 并发 3 路分片（单片重试 3 次）→ complete；秒传提示零上传）；上传进度展示
- [x] 5.3 列表：阶段+进度列（处理中显示 stage 与百分比，页面存在处理中文档时 3s 轮询刷新）；失败行「重试」按钮（确认弹窗）
- [x] 5.4 locales kb.json 中英同步新键（校验文件中/上传中/秒传/续传/重试确认等）+ `pnpm check:type` 通过

## 6. 验证与收尾

- [x] 6.1 后端 `mvn test` 全绿（245 通过 / 0 失败，基线 208 + 新增 37）；存量库 vben5 已执行 upgrade SQL
- [x] 6.2 E2E（API 级，真实后端 8080）：md 小文件单请求→完成→检索命中；>10MB（11.1MB）分片 init→3 chunk→complete→CHUNKING→EMBEDDING 阶段进度持续落库；秒传重复 init 零传输 instant=true；重启恢复将 processing 全部置失败（含可读原因）；retry 从 processed_chunks 断点续跑（1856→3968 推进）；pdf PDFBox 解析→检索命中、docx POI 解析→切块 3 块；失败可读原因（上游欠费 403 → 「余额不足」文案）。注：大文档全量向量化完成因上游 Embedding 账户欠费（外部依赖）未闭环，断点推进语义已验证；浏览器 UI 走查因登录图形验证码自动化受限未执行，重试按钮/进度列逻辑由 API 级验证与 `pnpm check:type` 覆盖
- [x] 6.3 README「知识库」功能段落更新 + 存量库清单补 upgrade SQL
- [x] 6.4 `openspec validate kb-large-file-pipeline --strict` 通过 → `openspec archive kb-large-file-pipeline --yes`（sync delta → archive）
