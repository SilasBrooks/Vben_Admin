import { requestClient } from '#/api/request';

/** Agent 技能（agent_skill）：Claude 式能力包，渐进式披露扩展助手能力 */
export interface SkillItem {
  builtIn: 0 | 1;
  content: string;
  createTime?: string;
  description: string;
  enabled: 0 | 1;
  id: number;
  name: string;
  /** 技能包原始 zip 文件名（无包为 null） */
  packageFileName?: null | string;
  /** 技能包 zip 存储键（无包为 null，手工技能） */
  packageFileKey?: null | string;
  /** 技能包字节数 */
  packageFileSize?: null | number;
  title: string;
}

/** 技能包内文件条目（清单） */
export interface SkillFileItem {
  /** 包内相对路径 */
  path: string;
  /** 实际字节数 */
  size: number;
}

/** 新增/编辑参数 */
export interface SkillSaveParams {
  content: string;
  description: string;
  name: string;
  title: string;
}

interface SkillPageResult {
  items: SkillItem[];
  total: number;
}

/** 技能分页列表（名称/标题模糊过滤，时间倒序） */
export async function getSkillListApi(params: {
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
}) {
  return requestClient.get<SkillPageResult>('/system/skill/list', { params });
}

/** 新增技能（name 唯一：小写字母/数字/连字符） */
export async function createSkillApi(data: SkillSaveParams) {
  return requestClient.post<SkillItem>('/system/skill', data);
}

/** 编辑技能（内置技能可编辑；启停状态由独立接口控制） */
export async function updateSkillApi(id: number, data: SkillSaveParams) {
  return requestClient.put<SkillItem>(`/system/skill/${id}`, data);
}

/** 删除技能（内置技能会被后端拒绝） */
export async function deleteSkillApi(id: number) {
  return requestClient.delete(`/system/skill/${id}`);
}

/** 启停技能（修改下一轮对话即生效，无需重启） */
export async function setSkillEnabledApi(id: number, enabled: boolean) {
  return requestClient.put(`/system/skill/${id}/enabled`, { enabled });
}

/** 上传技能包（zip ≤10MB）：解析 SKILL.md frontmatter 建技能，默认停用 */
export async function uploadSkillPackageApi(file: File) {
  return await requestClient.upload<SkillItem>('/system/skill/package', { file });
}

/** 技能包内文件清单（路径 + 字节数） */
export async function getSkillFilesApi(id: number) {
  return await requestClient.get<{ items: SkillFileItem[]; total: number }>(
    `/system/skill/${id}/files`,
  );
}

/** 下载包内单个文件（按路径精确匹配提取），返回 Blob */
export async function downloadSkillFileApi(id: number, path: string) {
  return await requestClient.download(`/system/skill/${id}/files/content`, {
    params: { path },
  });
}

/** 下载技能原包（上传时的原始 zip），返回 Blob */
export async function downloadSkillPackageApi(id: number) {
  return await requestClient.download(`/system/skill/${id}/package`);
}
