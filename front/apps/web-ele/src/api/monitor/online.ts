import { requestClient } from '#/api/request';

export interface OnlineUserItem {
  userId: number;
  username: string;
  nickname?: string;
  loginTime?: string;
  ip?: string;
}

interface OnlinePageResult {
  items: OnlineUserItem[];
  total: number;
}

/** 在线用户列表（Redis 实时会话，用户名模糊 + 分页） */
export async function getOnlineUserListApi(params: {
  pageNo?: number;
  pageSize?: number;
  username?: string;
}) {
  return requestClient.get<OnlinePageResult>('/monitor/online/list', { params });
}

/** 强制下线 */
export async function kickUserApi(userId: number) {
  return requestClient.post(`/monitor/online/${userId}/kick`);
}
