import { requestClient } from '#/api/request';

/** 部门节点（树形，children 可选） */
export interface DeptNode {
  id: number;
  parentId: number;
  deptName: string;
  leader?: string;
  status?: number;
  orderNum?: number;
  remark?: string;
  createTime?: string;
  children?: DeptNode[];
}

/** 部门树 */
export async function getDeptTreeApi() {
  return requestClient.get<DeptNode[]>('/system/dept/list');
}

/** 部门详情 */
export async function getDeptDetailApi(id: number) {
  return requestClient.get<DeptNode>(`/system/dept/${id}`);
}

/** 新增部门 */
export async function createDeptApi(data: Partial<DeptNode>) {
  return requestClient.post('/system/dept/save', data);
}

/** 更新部门 */
export async function updateDeptApi(data: Partial<DeptNode>) {
  return requestClient.put('/system/dept/update', data);
}

/** 删除部门 */
export async function deleteDeptApi(id: number) {
  return requestClient.delete(`/system/dept/${id}`);
}
