import { requestClient } from '#/api/request';

// SMTP 的连接、TLS、认证和投递需要多轮通信，不能沿用普通请求的 10 秒超时。
const EMAIL_CODE_TIMEOUT = 60_000;

export interface CaptchaVerification {
  captchaId: string;
  captchaCode: string;
}
export interface EmailChallenge {
  challengeId: string;
}
export interface RecoveryEmailInfo {
  enabled: boolean;
  email: string;
}

export const getRecoveryOptionsApi = () =>
  requestClient.get<{ enabled: boolean }>('/auth/recovery/options');
export const sendRecoveryCodeApi = (
  data: CaptchaVerification & { username: string },
) =>
  requestClient.post<EmailChallenge>('/auth/recovery/code', data, {
    timeout: EMAIL_CODE_TIMEOUT,
  });
export const resetForgottenPasswordApi = (
  data: EmailChallenge & {
    code: string;
    newPassword: string;
    confirmPassword: string;
  },
) => requestClient.post<void>('/auth/recovery/reset', data);
export const getRecoveryEmailApi = () =>
  requestClient.get<RecoveryEmailInfo>('/user/recovery-email');
export const sendBindingCodeApi = (data: { email: string; password: string }) =>
  requestClient.post<EmailChallenge>('/user/recovery-email/code', data, {
    timeout: EMAIL_CODE_TIMEOUT,
  });
export const bindRecoveryEmailApi = (
  data: EmailChallenge & { code: string; password: string },
) => requestClient.post<void>('/user/recovery-email', data);
