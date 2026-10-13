import { requestClient } from '#/api/request';

/** 知识库文档（kb_doc）：status 0=处理中 1=完成 2=失败；stage 为处理阶段（PARSING/CHUNKING/EMBEDDING） */
export interface KbDocItem {
  chunkCount: number;
  createTime?: string;
  errorMsg?: null | string;
  fileName: string;
  id: number;
  processedChunks?: number;
  progress?: number;
  stage?: null | string;
  sizeBytes: number;
  status: 0 | 1 | 2;
  totalChunks?: number;
  uploaderName: string;
}

/** 混合检索命中片段（score 为 RRF 融合分，matchType 标识命中通道；rerankScore 有值=经精排） */
export interface KbSearchHit {
  content: string;
  docId: number;
  fileName: string;
  id: number;
  matchType: 'both' | 'fts' | 'vector';
  rerankScore?: null | number;
  score: number;
  seq: number;
  vecScore: number;
}

/** 分片上传会话信息（init 返回；秒传命中只含 instant/docId） */
export interface KbUploadInitResult {
  chunkSize?: number;
  docId?: number;
  instant?: boolean;
  receivedChunks?: number[];
  totalChunks?: number;
  uploadId?: string;
}

interface KbPageResult {
  items: KbDocItem[];
  total: number;
}

/** 文档分页列表（文件名模糊过滤，含处理状态与失败原因） */
export async function getKbDocListApi(params: {
  fileName?: string;
  pageNo?: number;
  pageSize?: number;
}) {
  return requestClient.get<KbPageResult>('/system/kb/list', { params });
}

/** 上传文档（md/txt/pdf/docx，10MB 上限）：受理后异步切块向量化，接口立即返回 */
export async function uploadKbDocApi(file: File) {
  return requestClient.upload<{ id: number }>('/system/kb/upload', { file });
}

/**
 * 分片上传-领取会话（>10MB）：幂等（同文件进行中会话直接复用，断点续传）；
 * SHA-256 命中已入库文件时秒传（instant=true，零上传）。
 */
export async function initKbUploadApi(data: {
  chunkSize: number;
  fileName: string;
  fileSize: number;
  sha256: string;
}) {
  return requestClient.post<KbUploadInitResult>(
    '/system/kb/doc/upload/init',
    data,
  );
}

/** 分片上传-上传单片：index 从 0 起，单片大小须与 init 声明一致（末片除外） */
export async function uploadKbChunkApi(
  uploadId: string,
  index: number,
  chunk: Blob,
) {
  const form = new FormData();
  form.append('index', String(index));
  form.append('chunk', chunk);
  return requestClient.post<void>(
    `/system/kb/doc/upload/${uploadId}/chunk`,
    form,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
}

/** 分片上传-完成：合并分片并校验整文件 SHA-256，返回新建文档 id */
export async function completeKbUploadApi(uploadId: string) {
  return requestClient.post<{ id: number }>(
    `/system/kb/doc/upload/${uploadId}/complete`,
  );
}

/** 分片上传-放弃：清理会话与已传分片（幂等，会话不存在静默成功） */
export async function abortKbUploadApi(uploadId: string) {
  return requestClient.delete(`/system/kb/doc/upload/${uploadId}`);
}

/** 重试失败文档：从已入库块断点续跑（仅失败状态可重试） */
export async function retryKbDocApi(id: number) {
  return requestClient.post<void>(`/system/kb/doc/${id}/retry`);
}

/** 删除文档：级联清理向量块与存储引用（处理中会被后端拒绝） */
export async function deleteKbDocApi(id: number) {
  return requestClient.delete(`/system/kb/${id}`);
}

/** 检索测试：混合检索 Top-K 相似片段（与 AI 工具 search_knowledge 同链路） */
export async function searchKbApi(query: string, topK = 4) {
  return requestClient.post<KbSearchHit[]>('/system/kb/search', {
    query,
    topK,
  });
}

/** 文档原文预览块：块序号与正文（kb_chunk 按 seq 升序即文档正文序列） */
export interface KbPreviewBlock {
  content: string;
  seq: number;
}

/** 文档原文预览：AI 溯源卡片「查看原文」数据源（仅要求登录态） */
export async function previewKbDocApi(id: number) {
  return requestClient.get<{ blocks: KbPreviewBlock[]; fileName: string }>(
    `/system/kb/doc/${id}/preview`,
  );
}
