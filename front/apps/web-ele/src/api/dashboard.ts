import { requestClient } from '#/api/request';

/** 统计卡总数块 */
export interface DashboardTotals {
  deptCount: number;
  dictTypeCount: number;
  fileCount: number;
  /** 累计登录记录数（成功+失败） */
  loginCount: number;
  onlineCount: number;
  /** 累计操作日志数 */
  operCount: number;
  roleCount: number;
  userCount: number;
}

/** 今日概况块 */
export interface DashboardToday {
  loginFail: number;
  loginSuccess: number;
  operCount: number;
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

/** 仪表盘聚合数据（GET /dashboard/summary，登录即可） */
export interface DashboardSummary {
  deptDistribution: DashboardDistItem[];
  loginTrend: DashboardTrendItem[];
  moduleDistribution: DashboardDistItem[];
  recentLogins: DashboardRecentLogin[];
  recentOpers: DashboardRecentOper[];
  today: DashboardToday;
  totals: DashboardTotals;
}

/** 获取仪表盘聚合数据 */
export async function getDashboardSummaryApi() {
  return requestClient.get<DashboardSummary>('/dashboard/summary');
}
