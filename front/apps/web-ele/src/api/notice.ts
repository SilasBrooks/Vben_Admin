import { requestClient } from '#/api/request';

/** 站内通知条目 */
export interface NoticeItem {
  content: string;
  createTime: string;
  id: number;
  msgType: string;
  /** 0 未读 1 已读 */
  readFlag: 0 | 1;
  readTime: null | string;
  title: string;
}

/** 通知分页结果（时间倒序） */
export interface NoticeListResult {
  items: NoticeItem[];
  total: number;
}

/** 获取通知列表（时间倒序） */
export async function getNoticeListApi(pageNum: number, pageSize: number) {
  return requestClient.get<NoticeListResult>('/notice/list', {
    params: { pageNum, pageSize },
  });
}

/** 获取未读数量 */
export async function getNoticeUnreadCountApi() {
  return requestClient.get<{ count: number }>('/notice/unread-count');
}

/** 标记指定通知为已读 */
export async function readNoticeApi(ids: number[]) {
  return requestClient.post('/notice/read', { ids });
}

/** 全部标记为已读 */
export async function readAllNoticeApi() {
  return requestClient.post('/notice/read-all');
}

/** 公告发布目标类型：all 全员 / dept 按部门 / user 按用户 */
export type AnnounceTargetType = 'all' | 'dept' | 'user';

/** 公告发布请求体 */
export interface AnnouncePayload {
  content?: string;
  deptIds?: number[];
  targetType: AnnounceTargetType;
  title: string;
  userIds?: number[];
}

/** 公告广播（管理员，复用站内通知通道） */
export async function announceApi(data: AnnouncePayload) {
  return requestClient.post<{ count: number }>('/notice/announce', data);
}
