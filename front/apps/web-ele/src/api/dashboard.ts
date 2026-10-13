import { requestClient } from '#/api/request';

/** 统计卡总数块（累计登录/操作数受 Monitor 权限控制，无权限为 null=不可见） */
export interface DashboardTotals {
  deptCount: number;
  dictTypeCount: number;
  fileCount: number;
  /** 累计登录记录数（成功+失败）；无 Monitor:LoginLog:List 权限时为 null */
  loginCount: number | null;
  onlineCount: number;
  /** 累计操作日志数；无 Monitor:OperLog:List 权限时为 null */
  operCount: number | null;
  roleCount: number;
  userCount: number;
}

/** 今日概况块（监控类计数受 Monitor 权限控制，无权限为 null=不可见） */
export interface DashboardToday {
  loginFail: number | null;
  loginSuccess: number | null;
  operCount: number | null;
}

/** 登录趋势单日数据 */
export interface DashboardTrendItem {
  date: string;
  fail: number;
  success: number;
}

/** 分布数据项（部门分布/模块分布共用） */
export interface DashboardDistItem {
  name: string;
  value: number;
}

/** 最近登录记录 */
export interface DashboardRecentLogin {
  ip: string;
  loginTime: string;
  /** 0 成功 1 失败 */
  status: number;
  username: string;
}

/** 最近操作记录 */
export interface DashboardRecentOper {
  costMs: number;
  description: string;
  module: string;
  operName: string;
  operTime: string;
  /** 0 成功 1 失败 */
  status: number;
}

/** AI Token 趋势单日数据 */
export interface DashboardTokenTrendItem {
  calls: number;
  date: string;
  tokens: number;
}

/** 模型用量分布项 */
export interface DashboardModelDistItem {
  calls: number;
  name: string;
  tokens: number;
}

/** AI 用量统计块（LLM 调用落库聚合，tokens 为 NULL 的记录计入次数不计入合计） */
export interface DashboardAiUsage {
  /** 近 30 天按模型 token Top5 */
  modelDistribution: DashboardModelDistItem[];
  /** 本月调用次数 */
  monthCalls: number;
  /** 本月 token 合计 */
  monthTokens: number;
  /** 今日调用次数 */
  todayCalls: number;
  /** 今日 token 合计 */
  todayTokens: number;
  /** 近 14 天每日 token 与调用次数（日期升序） */
  tokenTrend: DashboardTokenTrendItem[];
  /** 累计调用次数（全部历史） */
  totalCalls: number;
  /** 累计 token 合计（全部历史） */
  totalTokens: number;
}

/**
 * 仪表盘聚合数据（GET /dashboard/summary，登录即可）。
 * 受控块（loginTrend/moduleDistribution/recentLogins/recentOpers 及 totals/today 中的监控计数）
 * 在无对应 Monitor 权限时为 null（语义为「不可见」而非 0）。
 */
export interface DashboardSummary {
  aiUsage: DashboardAiUsage;
  deptDistribution: DashboardDistItem[];
  /** 无 Monitor:LoginLog:List 权限时为 null */
  loginTrend: DashboardTrendItem[] | null;
  /** 无 Monitor:OperLog:List 权限时为 null */
  moduleDistribution: DashboardDistItem[] | null;
  /** 无 Monitor:LoginLog:List 权限时为 null */
  recentLogins: DashboardRecentLogin[] | null;
  /** 无 Monitor:OperLog:List 权限时为 null */
  recentOpers: DashboardRecentOper[] | null;
  today: DashboardToday;
  totals: DashboardTotals;
}

/** 获取仪表盘聚合数据 */
export async function getDashboardSummaryApi() {
  return requestClient.get<DashboardSummary>('/dashboard/summary');
}
