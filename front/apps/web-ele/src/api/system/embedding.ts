import { requestClient } from '#/api/request';

/** 协议风格：standard=OpenAI 兼容 /embeddings；ark-multimodal=方舟 /embeddings/multimodal */
export type EmbProtocol = 'ark-multimodal' | 'standard';

/** Embedding 模型配置（sys_embedding_config），api_key 返回已脱敏值 */
export interface EmbItem {
  apiKey: string;
  baseUrl: string;
  createTime?: string;
  dimension?: null | number;
  enabled: 0 | 1;
  id: number;
  isActive: 0 | 1;
  model: string;
  name: string;
  protocol: EmbProtocol;
  remark?: null | string;
  timeoutSeconds: number;
}

/** 新增/编辑参数：apiKey 为空/缺省 = 保持原值（仅编辑） */
export interface EmbSaveParams {
  apiKey?: string;
  baseUrl: string;
  enabled?: 0 | 1;
  model: string;
  name: string;
  protocol?: EmbProtocol;
  remark?: null | string;
  timeoutSeconds?: number;
}

interface EmbPageResult {
  items: EmbItem[];
  total: number;
}

/** Embedding 配置分页列表（名称模糊过滤，激活配置置顶） */
export async function getEmbListApi(params: {
  name?: string;
  pageNo?: number;
  pageSize?: number;
}) {
  return requestClient.get<EmbPageResult>('/system/embedding/list', { params });
}

/** 新增 Embedding 配置 */
export async function createEmbApi(data: EmbSaveParams) {
  return requestClient.post<EmbItem>('/system/embedding', data);
}

/** 编辑 Embedding 配置（apiKey 留空 = 不修改） */
export async function updateEmbApi(id: number, data: EmbSaveParams) {
  return requestClient.put<EmbItem>(`/system/embedding/${id}`, data);
}

/** 删除 Embedding 配置（激活中会被后端拒绝） */
export async function deleteEmbApi(id: number) {
  return requestClient.delete(`/system/embedding/${id}`);
}

/** 激活为全局 Embedding（知识库向量化/检索使用） */
export async function activateEmbApi(id: number) {
  return requestClient.post(`/system/embedding/${id}/activate`);
}

/** 连通测试：真实向量化一条样本，返回实测维度并回填配置 */
export async function testEmbApi(id: number, timeoutSeconds = 60) {
  return requestClient.post<{
    dimension: number;
    elapsedMs: number;
    ok: boolean;
  }>(`/system/embedding/${id}/test`, undefined, {
    timeout: (timeoutSeconds + 5) * 1000,
  });
}
