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
