import { requestClient } from '#/api/request';

export interface TableColumnConfig {
  field: string;
  visible?: boolean;
  width?: number;
  fixed?: '' | 'left' | 'right';
  children?: TableColumnConfig[];
}

/** 获取当前用户配置；menu 为按权限生成的只读菜单路由树。 */
export function getUserConfigApi<T = unknown>(key: string) {
  return requestClient.get<T>('/user-config', {
    params: { key },
    timeout: 10_000,
  });
}

export function saveUserConfigApi(key: string, value: TableColumnConfig[]) {
  return requestClient.post('/user-config/save', { key, value });
}
