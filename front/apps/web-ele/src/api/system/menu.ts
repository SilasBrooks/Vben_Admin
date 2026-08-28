import { requestClient } from '#/api/request';

/** 菜单节点（树形，children 可选） */
export interface MenuNode {
  id: number;
  parentId: number;
  menuName: string;
  menuType: 'M' | 'C' | 'F';
  title: string;
  icon?: string;
  orderNum?: number;
  path?: string;
  component?: string;
  perm?: string;
  visible?: number;
  keepAlive?: number;
  affixTab?: number;
  status?: number;
  authority?: string;
  children?: MenuNode[];
}

/** 菜单树 */
export async function getMenuTreeApi() {
  return requestClient.get<MenuNode[]>('/system/menu/list');
}

/** 菜单详情 */
export async function getMenuDetailApi(id: number) {
  return requestClient.get<MenuNode>(`/system/menu/${id}`);
}

/** 新增菜单 */
export async function createMenuApi(data: Partial<MenuNode>) {
  return requestClient.post('/system/menu/save', data);
}

/** 更新菜单 */
export async function updateMenuApi(data: Partial<MenuNode>) {
  return requestClient.put('/system/menu/update', data);
}

/** 删除菜单 */
export async function deleteMenuApi(id: number) {
  return requestClient.delete(`/system/menu/${id}`);
}
