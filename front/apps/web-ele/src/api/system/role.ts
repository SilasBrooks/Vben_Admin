import { requestClient } from '#/api/request';

export interface RoleItem {
  id: number;
  roleKey: string;
  roleName: string;
  sortNum?: number;
  status?: number;
  /** 数据范围：1全部 2自定义部门 3本部门 4本部门及以下 5仅本人 */
  dataScope?: string;
}

export interface RolePageResult {
  items: RoleItem[];
  total: number;
}

/** 角色分页列表（支持角色名模糊 + 状态过滤） */
export async function getRoleListApi(params: {
  pageNo?: number;
  pageSize?: number;
  roleName?: string;
  status?: number;
}) {
  return requestClient.get<RolePageResult>('/system/role/list', { params });
}

/** 角色下拉选项 */
export async function getRoleOptionsApi() {
  return requestClient.get<RoleItem[]>('/system/role/options');
}

/** 角色详情 */
export async function getRoleDetailApi(id: number) {
  return requestClient.get<RoleItem>(`/system/role/${id}`);
}

/** 新增角色，返回新角色 id */
export async function createRoleApi(data: Partial<RoleItem>) {
  return requestClient.post<number>('/system/role/save', data);
}

/** 更新角色 */
export async function updateRoleApi(data: Partial<RoleItem>) {
  return requestClient.put('/system/role/update', data);
}

/** 删除角色 */
export async function deleteRoleApi(id: number) {
  return requestClient.delete(`/system/role/${id}`);
}

/** 角色已分配菜单 id 列表 */
export async function getRoleMenuIdsApi(roleId: number) {
  return requestClient.get<number[]>(`/system/role/menu-ids/${roleId}`);
}

/** 分配菜单给角色 */
export async function assignRoleMenusApi(roleId: number, menuIds: number[]) {
  return requestClient.post('/system/role/assign', { roleId, menuIds });
}

/** 角色数据范围配置（dataScope + 自定义部门 id 集合） */
export async function getDataScopeApi(roleId: number) {
  return requestClient.get<{ dataScope: string; deptIds: number[] }>(
    `/system/role/data-scope/${roleId}`,
  );
}

/** 更新角色数据范围 */
export async function updateDataScopeApi(
  roleId: number,
  dataScope: string,
  deptIds?: number[],
) {
  return requestClient.put('/system/role/data-scope', {
    roleId,
    dataScope,
    deptIds,
  });
}
