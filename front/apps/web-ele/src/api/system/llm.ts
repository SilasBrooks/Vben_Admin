import { requestClient } from '#/api/request';

/** 模型配置（sys_llm_config），api_key 返回已脱敏值 */
export interface LlmItem {
  apiKey: string;
  baseUrl: string;
  createTime?: string;
  enabled: 0 | 1;
  id: number;
  isActive: 0 | 1;
  maxTokens?: null | number;
  model: string;
  name: string;
  remark?: null | string;
  temperature?: null | number;
  timeoutSeconds: number;
}

/** 新增/编辑参数：apiKey 为空/缺省 = 保持原值（仅编辑） */
export interface LlmSaveParams {
  apiKey?: string;
  baseUrl: string;
  enabled?: 0 | 1;
  maxTokens?: null | number;
  model: string;
  name: string;
  remark?: null | string;
  temperature?: null | number;
  timeoutSeconds?: number;
}

interface LlmPageResult {
  items: LlmItem[];
  total: number;
}

/** 模型配置分页列表（名称模糊过滤，激活模型置顶） */
export async function getLlmListApi(params: {
  name?: string;
  pageNo?: number;
  pageSize?: number;
}) {
  return requestClient.get<LlmPageResult>('/system/llm/list', { params });
}

/** 新增模型配置 */
export async function createLlmApi(data: LlmSaveParams) {
  return requestClient.post<LlmItem>('/system/llm', data);
}

/** 编辑模型配置（apiKey 留空 = 不修改） */
export async function updateLlmApi(id: number, data: LlmSaveParams) {
  return requestClient.put<LlmItem>(`/system/llm/${id}`, data);
}

/** 删除模型配置（激活中会被后端拒绝） */
export async function deleteLlmApi(id: number) {
  return requestClient.delete(`/system/llm/${id}`);
}

/** 激活为 AI 助手当前全局模型（即时生效） */
export async function activateLlmApi(id: number) {
  return requestClient.post(`/system/llm/${id}/activate`);
}

/** 连通测试：真实发一次最小补全请求（思考型模型推理耗时较长，按配置超时+5s 覆盖请求级默认 10s） */
export async function testLlmApi(id: number, timeoutSeconds = 60) {
  return requestClient.post<{
    elapsedMs: number;
    model: string;
    ok: boolean;
    reply: string;
  }>(`/system/llm/${id}/test`, undefined, {
    timeout: (timeoutSeconds + 5) * 1000,
  });
}
