import { useAccessStore } from '@vben/stores';

import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  /** 登录接口参数 */
  export interface LoginParams {
    password?: string;
    username?: string;
    /** 服务端图形验证码 id（GET /auth/captcha 返回） */
    captchaId?: string;
    /** 用户输入的验证码 */
    captchaCode?: string;
  }

  /** 图形验证码返回值 */
  export interface CaptchaResult {
    captchaId: string;
    /** base64 PNG（data:image/png;base64,...） */
    image: string;
    /** 开发联调回显明文（生产 profile 为空） */
    devCode?: string;
  }

  /** 登录接口返回值 */
  export interface LoginResult {
    accessToken: string;
  }

  export interface RefreshTokenResult {
    data: string;
    status: number;
  }
}

/**
 * 获取登录图形验证码（2 分钟有效、一次性使用）
 */
export async function getCaptchaApi() {
  return requestClient.get<AuthApi.CaptchaResult>('/auth/captcha');
}

/**
 * 登录
 */
export async function loginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>('/auth/login', data);
}

/**
 * 刷新accessToken
 */
export async function refreshTokenApi() {
  return baseRequestClient.post<AuthApi.RefreshTokenResult>(
    '/auth/refresh',
    null,
    { withCredentials: true },
  );
}

/**
 * 退出登录
 *
 * 裸 client 不带全局 Authorization 拦截器，这里手动补 Bearer 头让后端定位并移除在线会话；
 * withCredentials 携带 httpOnly refresh cookie（后端再做一层兜底）。
 */
export async function logoutApi() {
  const accessStore = useAccessStore();
  return baseRequestClient.post('/auth/logout', null, {
    headers: {
      Authorization: accessStore.accessToken
        ? `Bearer ${accessStore.accessToken}`
        : undefined,
    },
    withCredentials: true,
  });
}

/**
 * 获取用户权限码
 */
export async function getAccessCodesApi() {
  return requestClient.get<string[]>('/auth/codes');
}

/**
 * 修改自己的密码（成功后本人所有 token 立即失效，需重新登录）
 */
export async function changePasswordApi(oldPassword: string, newPassword: string) {
  return requestClient.post<void>('/auth/change-password', { newPassword, oldPassword });
}
