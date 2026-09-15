import { requestClient } from '#/api/request';

export interface DictTypeItem {
  id: number;
  dictName: string;
  dictType: string;
  status: number;
  remark?: string;
  createTime?: string;
}

export interface DictDataItem {
  id: number;
  dictType: string;
  dictLabel: string;
  dictValue: string;
  sortNum?: number;
  status?: number;
  remark?: string;
}

export interface DictDataPageResult {
  items: DictDataItem[];
  total: number;
}

/** 全部字典类型（量小不分页） */
export async function getDictTypeListApi() {
  return requestClient.get<DictTypeItem[]>('/system/dict/type/list');
}

/** 新增字典类型 */
export async function createDictTypeApi(data: Partial<DictTypeItem>) {
  return requestClient.post('/system/dict/type/save', data);
}

/** 编辑字典类型 */
export async function updateDictTypeApi(data: Partial<DictTypeItem>) {
  return requestClient.put('/system/dict/type/update', data);
}

/** 删除字典类型（级联删除其数据项） */
export async function deleteDictTypeApi(id: number) {
  return requestClient.delete(`/system/dict/type/${id}`);
}

/** 按类型分页查询字典数据 */
export async function getDictDataListApi(params: {
  dictType: string;
  pageNo?: number;
  pageSize?: number;
}) {
  return requestClient.get<DictDataPageResult>('/system/dict/data/list', {
    params,
  });
}

/** 新增字典数据 */
export async function createDictDataApi(data: Partial<DictDataItem>) {
  return requestClient.post('/system/dict/data/save', data);
}

/** 编辑字典数据 */
export async function updateDictDataApi(data: Partial<DictDataItem>) {
  return requestClient.put('/system/dict/data/update', data);
}

/** 删除字典数据 */
export async function deleteDictDataApi(id: number) {
  return requestClient.delete(`/system/dict/data/${id}`);
}

/** 下拉选项（仅要求登录，启用项按 sort 升序） */
export async function getDictOptionsApi(dictType: string) {
  return requestClient.get<DictDataItem[]>(
    `/system/dict/data/options/${dictType}`,
  );
}
