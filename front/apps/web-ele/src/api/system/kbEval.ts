import { requestClient } from '#/api/request';

/** 评测集列表项：caseCount 为用例数，lastMrr 为最近一次运行 MRR（未运行过为 null） */
export interface KbEvalSetItem {
  caseCount: number;
  createTime?: string;
  description?: null | string;
  id: number;
  lastMrr?: null | number;
  lastRunId?: null | number;
  name: string;
}

/** 评测用例列表项：expectDocName 为期望文档名快照回显（文档被删后为空） */
export interface KbEvalCaseItem {
  createTime?: string;
  expectDocId: number;
  expectDocName?: null | string;
  expectSeq?: null | number;
  id: number;
  note?: null | string;
  query: string;
  setId: number;
}

/** Top-5 命中片段摘要（运行时快照；rerankScore 有值=经精排） */
export interface KbEvalHitBrief {
  docId: number;
  fileName: string;
  rerankScore?: null | number;
  score: number;
  seq: number;
}

/** 逐用例结果（快照）：rank 为期望命中排名（1 起始），null=Top-K 内未命中 */
export interface KbEvalCaseResult {
  expectDocId: number;
  expectDocName?: null | string;
  expectSeq?: null | number;
  query: string;
  rank?: null | number;
  topHits: KbEvalHitBrief[];
}

/** 运行记录：快照语义（rerankActive=执行时精排是否激活；detail 仅报告接口返回） */
export interface KbEvalRunItem {
  createTime?: string;
  elapsedMs: number;
  embeddingModel: string;
  hitAt1: number;
  hitAt3: number;
  hitAt5: number;
  id: number;
  mrr: number;
  rerankActive: boolean;
  setId: number;
  setName: string;
  topK: number;
  totalCases: number;
  detail?: KbEvalCaseResult[];
}

interface KbEvalSetPageResult {
  items: KbEvalSetItem[];
  total: number;
}

/** 评测集分页列表（名称模糊过滤，附用例数与最近一次运行 MRR 概览） */
export async function getKbEvalSetsApi(params: {
  name?: string;
  pageNo?: number;
  pageSize?: number;
}) {
  return requestClient.get<KbEvalSetPageResult>('/system/kb-eval/sets', {
    params,
  });
}

/** 新增评测集（名称唯一） */
export async function createKbEvalSetApi(data: {
  description?: string;
  name: string;
}) {
  return requestClient.post<KbEvalSetItem>('/system/kb-eval/sets', data);
}

/** 编辑评测集（不影响历史运行记录，快照语义） */
export async function updateKbEvalSetApi(
  id: number,
  data: { description?: string; name: string },
) {
  return requestClient.put<KbEvalSetItem>(`/system/kb-eval/sets/${id}`, data);
}

/** 删除评测集：级联删除其全部用例与运行记录 */
export async function deleteKbEvalSetApi(id: number) {
  return requestClient.delete(`/system/kb-eval/sets/${id}`);
}

/** 评测用例列表（按创建顺序，附期望文档名回显） */
export async function getKbEvalCasesApi(setId: number) {
  return requestClient.get<KbEvalCaseItem[]>(
    `/system/kb-eval/sets/${setId}/cases`,
  );
}

/** 新增评测用例（期望文档必须为知识库真实存在的文档） */
export async function createKbEvalCaseApi(
  setId: number,
  data: { expectDocId: number; expectSeq?: null | number; note?: string; query: string },
) {
  return requestClient.post<KbEvalCaseItem>(
    `/system/kb-eval/sets/${setId}/cases`,
    data,
  );
}

/** 编辑评测用例 */
export async function updateKbEvalCaseApi(
  id: number,
  data: { expectDocId: number; expectSeq?: null | number; note?: string; query: string },
) {
  return requestClient.put<KbEvalCaseItem>(
    `/system/kb-eval/cases/${id}`,
    data,
  );
}

/** 删除评测用例（不影响历史运行记录） */
export async function deleteKbEvalCaseApi(id: number) {
  return requestClient.delete(`/system/kb-eval/cases/${id}`);
}

/** 执行评测：逐用例检索（Top-5，含激活精排）并计算 Hit@1/3/5 与 MRR */
export async function runKbEvalApi(setId: number) {
  return requestClient.post<KbEvalRunItem>(
    `/system/kb-eval/sets/${setId}/runs`,
  );
}

/** 运行历史（快照记录，含精排激活状态标注） */
export async function getKbEvalRunsApi(setId: number) {
  return requestClient.get<KbEvalRunItem[]>(
    `/system/kb-eval/sets/${setId}/runs`,
  );
}

/** 运行报告详情：指标 + 逐用例命中排名与 Top-5 命中片段明细 */
export async function getKbEvalRunApi(id: number) {
  return requestClient.get<KbEvalRunItem>(`/system/kb-eval/runs/${id}`);
}
