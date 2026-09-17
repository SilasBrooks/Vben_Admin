import { requestClient } from '#/api/request';

/** 文件记录（sys_file），列表与上传返回体 */
export interface FileItem {
  bizType: string;
  contentType: string;
  createTime?: string;
  id: number;
  originalName: string;
  size: number;
  storageKey?: string;
  uploaderId: number;
  uploaderName?: string;
  url?: string;
}

interface FilePageResult {
  items: FileItem[];
  total: number;
}

/** 文件分页列表（原始名模糊过滤） */
export async function getFileListApi(params: {
  pageNo?: number;
  pageSize?: number;
  originalName?: string;
}) {
  return requestClient.get<FilePageResult>('/file/list', { params });
}

/** 上传通用文件（白名单 + 10MB） */
export async function uploadFileApi(file: File) {
  return requestClient.upload<FileItem>('/file/upload', { file });
}

/** 上传本人头像（仅图片 + 5MB，无需权限码） */
export async function uploadAvatarApi(file: File) {
  return requestClient.upload<FileItem>('/file/avatar', { file });
}

/** 删除文件（记录与物理文件同步移除） */
export async function deleteFileApi(id: number) {
  return requestClient.delete(`/file/${id}`);
}

/** 下载/预览文件内容（需登录，返回 Blob；图片可 objectURL 预览） */
export async function downloadFileApi(id: number) {
  return requestClient.download<Blob>(`/file/${id}/content`);
}
