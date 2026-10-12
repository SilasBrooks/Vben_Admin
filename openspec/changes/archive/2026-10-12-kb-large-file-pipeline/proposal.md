# Proposal: kb-large-file-pipeline

## Why

知识库单文件上限 10MB 且仅支持 md/txt，无法承载真实知识库场景的大文件（报告/手册类 PDF、Word 动辄几 MB～几十 MB）。用户明确提出「知识库一般都是好几 M 甚至几 G 的，该如何解决大文件」。需要一次传输层与处理层的成体系升级，同时补齐分片上传、断点续传、秒传、解析格式扩展、管道进度与断点续跑等生产级能力（均为面试高频考点，简历价值高）。

## What Changes

- **分片上传协议**（KB 文档专用）：init（按 sha256+size 幂等领取 uploadId，返回已传分片列表）→ 逐片上传（单片 5MB，流式落盘不经内存）→ complete（顺序合并 + 整文件 SHA-256 校验 + 入库）→ abort（放弃并清理）。支持**断点续传**（init 返回已收分片，前端跳过）与**秒传**（整文件哈希命中已入库文件则零传输建文档）。
- **单文件上限 10MB → 200MB**：仅分片路径放宽；≤10MB 小文件保留现有单请求上传路径不动，存量数据兼容。
- **文档格式扩展**：白名单从 md/txt 扩展到 **md/txt/pdf/docx**——PDFBox 按页流式解析 PDF，POI 按段落解析 docx；扫描件等无文本 PDF 明确报错不静默入库。
- **异步管道增强**：处理状态细化为阶段（PARSING/CHUNKING/EMBEDDING）+ 进度百分比（0-100）；embedding 按批入库并持久化断点（processed_chunks），**失败重试从断点续跑**；单文档 chunk 总量配额保护；服务重启时处理中文档标记失败（原因可读），重试不从头再来。
- **存储与去重**：大文件正文不再入库（DB text 仅存 ≤10MB 小文件正文，兼容现状），合并后经 StorageService（local/minio 可插拔）存储；新增文件实体按 SHA-256 去重（引用计数，删除最后一个引用文档时清理）。
- **前端**：上传按文件大小自动选路（≤10MB 原路径 / >10MB 分片并发 3 路）；列表新增阶段+进度列（处理中轮询刷新）；失败行提供「重试」按钮。

## Capabilities

- 修改 `ai/knowledge-base`：文档上传与格式校验（MODIFIED）、切块与异步向量化（MODIFIED）、文档列表与删除（MODIFIED）、前端知识库页面（MODIFIED）；新增大文件分片上传需求（ADDED）。

## Impact

- **后端**：kb 模块新增分片会话/文件实体与解析器抽象；KbDocService/Controller 扩展；`schema-postgres.sql` + 存量库 upgrade SQL（kb_doc 加列、新表 kb_file / kb_upload_session）；pom 新增 pdfbox、poi-ooxml 依赖。
- **前端**：`api/system/kb.ts` 分片 API；kb/index.vue 上传选路与进度/重试。
- **权限**：复用 Kb:Doc:Upload/Delete/List，零新增权限码。
- **配置**：分片临时目录、单片大小、并发度、chunk 配额为服务端常量/配置项。
- **不改动**：向量检索、嵌入配置、AI 对话引用链路均不受影响；≤10MB 原上传语义不变。
