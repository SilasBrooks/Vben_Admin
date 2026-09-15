import { requestClient } from '#/api/request';

export interface OperLogItem {
  id: number;
  operUserId?: number;
  operName?: string;
  module: string;
  description?: string;
  method?: string;
  requestMethod?: string;
  requestUrl?: string;
  params?: string;
  status: number;
  errorMsg?: string;
  ip?: string;
  costMs?: number;
  operTime?: string;
}

export interface LoginLogItem {
  id: number;
  username: string;
  status: number;
  message?: string;
  ip?: string;
  userAgent?: string;
  loginTime?: string;
}

interface LogPageResult<T> {
  items: T[];
  total: number;
}

interface LogQueryParams {
  pageNo?: number;
  pageSize?: number;
  beginTime?: string;
  endTime?: string;
  status?: number;
}

/** 操作日志分页列表（操作人模糊 + 状态 + 时间范围） */
export async function getOperLogListApi(
  params: LogQueryParams & { operName?: string },
) {
  return requestClient.get<LogPageResult<OperLogItem>>('/monitor/oper-log/list', {
    params,
  });
}

/** 删除单条操作日志 */
export async function deleteOperLogApi(id: number) {
  return requestClient.delete(`/monitor/oper-log/${id}`);
}

/** 清空操作日志 */
export async function clearOperLogApi() {
  return requestClient.delete('/monitor/oper-log/clear');
}

/** 登录日志分页列表（用户名模糊 + 状态 + 时间范围） */
export async function getLoginLogListApi(
  params: LogQueryParams & { username?: string },
) {
  return requestClient.get<LogPageResult<LoginLogItem>>(
    '/monitor/login-log/list',
    { params },
  );
}

/** 删除单条登录日志 */
export async function deleteLoginLogApi(id: number) {
  return requestClient.delete(`/monitor/login-log/${id}`);
}

/** 清空登录日志 */
export async function clearLoginLogApi() {
  return requestClient.delete('/monitor/login-log/clear');
}
