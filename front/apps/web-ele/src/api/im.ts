import { requestClient } from '#/api/request';

/** 联系人（后端仅暴露非敏感字段） */
export interface ImPeer {
  id: number;
  nickname: null | string;
  username: string;
}

/** IM 单聊消息 */
export interface ImMessage {
  content: string;
  createTime: string;
  id: number;
  /** 以接收人视角：0 未读 1 已读 */
  readFlag: 0 | 1;
  receiverId: number;
  senderId: number;
}

/** 会话：聊天对方 + 最后一条消息 + 未读数 */
export interface ImConversation {
  lastMessage: null | {
    content: string;
    createTime: string;
    id: number;
    senderId: number;
  };
  peer: ImPeer;
  unreadCount: number;
}

/** 联系人列表（启用用户，除自己） */
export async function getImPeersApi() {
  return requestClient.get<ImPeer[]>('/im/peers');
}

/** 会话列表（最近活跃在前） */
export async function getImConversationsApi() {
  return requestClient.get<ImConversation[]>('/im/conversations');
}

/** 历史消息：beforeId 为游标（首页不传），时间正序返回 */
export async function getImMessagesApi(peerId: number, beforeId?: number, pageSize = 20) {
  return requestClient.get<ImMessage[]>('/im/messages', {
    params: { peerId, pageSize, ...(beforeId ? { beforeId } : {}) },
  });
}

/** 发送消息，返回落库后的消息 */
export async function sendImMessageApi(receiverId: number, content: string) {
  return requestClient.post<{ message: ImMessage }>('/im/messages', {
    content,
    receiverId,
  });
}

/** 把对方发来的本人未读消息标记已读 */
export async function readImMessagesApi(peerId: number) {
  return requestClient.post<{ updated: number }>('/im/messages/read', { peerId });
}
