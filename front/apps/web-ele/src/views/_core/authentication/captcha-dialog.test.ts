import type { App } from 'vue';

import { createApp, nextTick } from 'vue';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import CaptchaDialog from './captcha-dialog.vue';
import Login from './login.vue';

const mocks = vi.hoisted(() => ({
  authLogin: vi.fn(),
  getCaptcha: vi.fn(),
  valid: true,
  loginValues: { username: 'test-user', password: 'incorrect' },
}));

vi.mock('#/api', () => ({ getCaptchaApi: mocks.getCaptcha }));
vi.mock('#/store', () => ({
  useAuthStore: () => ({ authLogin: mocks.authLogin, loginLoading: false }),
}));
vi.mock('#/locales', () => ({ $t: (key: string) => key }));
vi.mock('@vben/locales', () => ({ $t: (key: string) => key }));

// 替换通用 UI 外壳，保留真实登录页与验证码组件之间的异步交互。
vi.mock('@vben/common-ui', async () => {
  const { defineComponent, h, ref } = await import('vue');
  const rule = { min: () => rule, regex: () => rule };
  return {
    z: { string: () => rule },
    AuthenticationLogin: defineComponent({
      emits: ['submit'],
      setup(_, { emit }) {
        return () =>
          h(
            'button',
            {
              'data-test': 'login',
              onClick: () => emit('submit', { ...mocks.loginValues }),
            },
            '登录',
          );
      },
    }),
    VbenButton: defineComponent({
      setup(_, { slots }) {
        return () => h('button', slots.default?.());
      },
    }),
    useVbenForm: () => [
      defineComponent({ setup: () => () => h('input') }),
      {
        resetForm: async () => {},
        validate: async () => ({ valid: mocks.valid }),
        getValues: async () => ({ captchaCode: '1234' }),
      },
    ],
    useVbenModal: (options: {
      onConfirm: () => Promise<void>;
      onOpenChange: (open: boolean) => void;
    }) => {
      const open = ref(false);
      const locked = ref(false);
      return [
        defineComponent({
          props: { confirmDisabled: Boolean },
          setup(props, { slots }) {
            return () =>
              open.value
                ? h('div', { role: 'dialog' }, [
                    slots.default?.(),
                    h(
                      'button',
                      {
                        'data-test': 'confirm',
                        disabled: locked.value || props.confirmDisabled,
                        onClick: options.onConfirm,
                      },
                      '确认',
                    ),
                  ])
                : null;
          },
        }),
        {
          open: () => {
            open.value = true;
            options.onOpenChange(true);
          },
          close: async () => {
            open.value = false;
            options.onOpenChange(false);
          },
          lock: () => {
            locked.value = true;
          },
          unlock: () => {
            locked.value = false;
          },
        },
      ];
    },
  };
});

let app: App;
let container: HTMLDivElement;

async function click(selector: string) {
  const button = container.querySelector<HTMLButtonElement>(selector);
  expect(button).not.toBeNull();
  button!.click();
  await nextTick();
}

async function waitForCaptcha() {
  await vi.waitFor(() => {
    expect(container.querySelector('[role="dialog"] img')).not.toBeNull();
    expect(
      container.querySelector<HTMLButtonElement>('[data-test="confirm"]')
        ?.disabled,
    ).toBe(false);
  });
}

describe('验证码失败交互', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.valid = true;
    mocks.loginValues.password = 'incorrect';
    mocks.getCaptcha.mockImplementation(async () => ({
      captchaId: `captcha-${mocks.getCaptcha.mock.calls.length}`,
      image: 'data:image/png;base64,test',
    }));
    container = document.createElement('div');
    document.body.append(container);
  });

  afterEach(() => {
    app?.unmount();
    container.remove();
  });

  it.each(['密码错误', '验证码错误或已过期', '请求限流', '网络异常'])(
    '登录因%s失败后关闭弹窗，再次登录使用新验证码和修改后的密码',
    async (message) => {
      mocks.authLogin
        .mockRejectedValueOnce(new Error(message))
        .mockResolvedValueOnce({});
      app = createApp(Login);
      app.mount(container);
      await click('[data-test="login"]');
      await waitForCaptcha();
      await click('[data-test="confirm"]');
      await vi.waitFor(() =>
        expect(container.querySelector('[role="dialog"]')).toBeNull(),
      );
      expect(mocks.getCaptcha).toHaveBeenCalledOnce();
      expect(mocks.authLogin).toHaveBeenCalledWith({
        username: 'test-user',
        password: 'incorrect',
        captchaId: 'captcha-1',
        captchaCode: '1234',
      });

      mocks.loginValues.password = 'corrected';
      await click('[data-test="login"]');
      await waitForCaptcha();
      await click('[data-test="confirm"]');
      await vi.waitFor(() =>
        expect(container.querySelector('[role="dialog"]')).toBeNull(),
      );
      expect(mocks.getCaptcha).toHaveBeenCalledTimes(2);
      expect(mocks.authLogin).toHaveBeenLastCalledWith({
        username: 'test-user',
        password: 'corrected',
        captchaId: 'captcha-2',
        captchaCode: '1234',
      });
    },
  );

  it('本地格式校验失败保留弹窗，不提交登录请求', async () => {
    mocks.valid = false;
    app = createApp(Login);
    app.mount(container);
    await click('[data-test="login"]');
    await waitForCaptcha();
    await click('[data-test="confirm"]');
    await waitForCaptcha();
    expect(mocks.authLogin).not.toHaveBeenCalled();
    expect(mocks.getCaptcha).toHaveBeenCalledOnce();
  });

  it('找回密码等默认调用失败后保持弹窗并刷新验证码', async () => {
    const verify = vi.fn().mockRejectedValue(new Error('send failed'));
    app = createApp(CaptchaDialog, { verify });
    const dialog = app.mount(container) as InstanceType<typeof CaptchaDialog>;
    dialog.open();
    await waitForCaptcha();
    await click('[data-test="confirm"]');
    await vi.waitFor(() => expect(mocks.getCaptcha).toHaveBeenCalledTimes(2));
    await waitForCaptcha();
    expect(verify).toHaveBeenCalledOnce();
  });
});
