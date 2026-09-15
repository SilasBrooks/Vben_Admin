import { requestClient } from '#/api/request';

export interface UserItem {
  id: number;
  username: string;
  password?: string;
  nickname?: string;
  email?: string;
  phone?: string;
  homePath?: string;
  deptId?: number;
  deptName?: string;
  status?: number;
  roleIds?: number[];
}

export interface UserPageResult {
  items: UserItem[];
  total: number;
}

/** 用户分页列表（支持用户名模糊 + 状态过滤） */
export async function getUserListApi(params: {
  pageNo?: number;
  pageSize?: number;
  username?: string;
  status?: number;
}) {
  return requestClient.get<UserPageResult>('/system/user/list', { params });
}

/** 用户详情（含已分配 roleIds） */
export async function getUserDetailApi(id: number) {
  return requestClient.get<UserItem>(`/system/user/${id}`);
}

/** 用户已分配的角色 id 列表（编辑回显） */
export async function getUserRoleIdsApi(id: number) {
  return requestClient.get<number[]>(`/system/user/${id}/role-ids`);
}

/** 新增用户 */
export async function createUserApi(data: Partial<UserItem>) {
  return requestClient.post('/system/user/save', data);
}

/** 编辑用户 */
export async function updateUserApi(data: Partial<UserItem>) {
  return requestClient.put('/system/user/update', data);
}

/** 删除用户 */
export async function deleteUserApi(id: number) {
  return requestClient.delete(`/system/user/${id}`);
}

/** 重置密码 */
export async function resetUserPasswordApi(id: number, newPassword: string) {
  return requestClient.post(`/system/user/${id}/reset-password`, { newPassword });
}

/** 分配角色（全量替换） */
export async function assignUserRolesApi(id: number, roleIds: number[]) {
  return requestClient.post(`/system/user/${id}/assign-roles`, { roleIds });
}
