import type { UserInfo } from '@vben/types';

import { requestClient } from '#/api/request';

/**
 * 获取用户信息
 */
export async function getUserInfoApi() {
  return requestClient.get<UserInfo>('/user/info');
}

/**
 * 修改本人资料（昵称 + 个人简介），返回更新后的用户信息
 */
export async function updateProfileApi(nickname: string, introduction: string) {
  return requestClient.patch<UserInfo>('/user/profile', { introduction, nickname });
}
