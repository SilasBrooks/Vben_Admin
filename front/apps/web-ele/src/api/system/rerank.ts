import { requestClient } from '#/api/request';

/** Rerank 精排模型配置（sys_rerank_config），api_key 返回已脱敏值 */
export interface RerankItem {
  apiKey: string;
  baseUrl: string;
  createTime?: string;
  enabled: 0 | 1;
  id: number;
  isActive: 0 | 1;
  model: string;
  name: string;
  remark?: null | string;
  timeoutSeconds: number;
}

/** 新增/编辑参数：apiKey 为空/缺省 = 保持原值（仅编辑） */
export interface RerankSaveParams {
  apiKey?: string;
  baseUrl: string;
  enabled?: 0 | 1;
  model: string;
  name: string;
  remark?: null | string;
  timeoutSeconds?: number;
}

interface RerankPageResult {
  items: RerankItem[];
  total: number;
}

/** Rerank 配置分页列表（名称模糊过滤，激活配置置顶） */
export async function getRerankListApi(params: {
  name?: string;
  pageNo?: number;
  pageSize?: number;
}) {
  return requestClient.get<RerankPageResult>('/system/rerank/list', { params });
}

/** 新增 Rerank 配置 */
export async function createRerankApi(data: RerankSaveParams) {
  return requestClient.post<RerankItem>('/system/rerank', data);
}

/** 编辑 Rerank 配置（apiKey 留空 = 不修改） */
export async function updateRerankApi(id: number, data: RerankSaveParams) {
  return requestClient.put<RerankItem>(`/system/rerank/${id}`, data);
}

/** 删除 Rerank 配置（激活中会被后端拒绝） */
export async function deleteRerankApi(id: number) {
  return requestClient.delete(`/system/rerank/${id}`);
}

/** 激活为全局 Rerank（知识库检索精排使用；未配置时检索为纯 RRF 融合） */
export async function activateRerankApi(id: number) {
  return requestClient.post(`/system/rerank/${id}/activate`);
}

/** 连通测试：真实精排一组样例文本，返回耗时与 Top-1 相关性得分 */
export async function testRerankApi(id: number, timeoutSeconds = 30) {
  return requestClient.post<{
    elapsedMs: number;
    ok: boolean;
    topScore: number;
  }>(`/system/rerank/${id}/test`, undefined, {
    timeout: (timeoutSeconds + 5) * 1000,
  });
}
