import type { RouteRecordRaw, Router } from 'vue-router';

import { createMemoryHistory, createRouter } from 'vue-router';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createRouterGuard } from './guard';
import { coreRoutes, fallbackNotFoundRoute } from './routes/core';

const mocks = vi.hoisted(() => {
  const userInfo = { homePath: '/workspace', roles: ['admin'] };
  const accessStore = {
    accessCodes: [] as string[],
    accessMenus: [] as { name: string; path: string }[],
    accessRoutes: [] as RouteRecordRaw[],
    accessToken: null as null | string,
    isAccessChecked: false,
    setAccessCodes(codes: string[]) {
      this.accessCodes = codes;
    },
    setAccessMenus(menus: { name: string; path: string }[]) {
      this.accessMenus = menus;
    },
    setAccessRoutes(routes: RouteRecordRaw[]) {
      this.accessRoutes = routes;
    },
    setIsAccessChecked(value: boolean) {
      this.isAccessChecked = value;
    },
  };
  const userStore = { userInfo: null as null | typeof userInfo };
  return {
    accessStore,
    userStore,
    fetchUserInfo: vi.fn(async () => (userStore.userInfo = userInfo)),
    getAccessCodes: vi.fn(async () => ['System:User:List']),
    generateAccess: vi.fn(async ({ router }: { router: Router }) => {
      const accessibleRoutes: RouteRecordRaw[] = [
        {
          path: '/system/user',
          name: 'SystemUser',
          component: { render: () => null },
        },
        {
          path: '/workspace',
          name: 'Workspace',
          component: { render: () => null },
        },
      ];
      accessibleRoutes.forEach((route) => router.addRoute(route));
      return {
        accessibleMenus: [{ name: 'SystemUser', path: '/system/user' }],
        accessibleRoutes,
      };
    }),
  };
});

vi.mock('@vben/preferences', () => ({
  preferences: {
    app: { defaultHomePath: '/workspace' },
    transition: { progress: false },
  },
}));
vi.mock('@vben/stores', () => ({
  useAccessStore: () => mocks.accessStore,
  useUserStore: () => mocks.userStore,
}));
vi.mock('@vben/utils', () => ({
  startProgress: vi.fn(),
  stopProgress: vi.fn(),
}));
vi.mock('#/api', () => ({ getAccessCodesApi: mocks.getAccessCodes }));
vi.mock('#/locales', () => ({ $t: (key: string) => key }));
vi.mock('#/router/routes', () => ({ accessRoutes: [] }));
vi.mock('#/store', () => ({
  useAuthStore: () => ({ fetchUserInfo: mocks.fetchUserInfo }),
}));
vi.mock('./access', () => ({ generateAccess: mocks.generateAccess }));

// 使用真实路由定义和元数据，仅替换与守卫无关的页面组件。
function stubPages(routes: RouteRecordRaw[]): RouteRecordRaw[] {
  return routes.map(
    (route) =>
      ({
        ...route,
        component: { render: () => null },
        ...(route.children ? { children: stubPages(route.children) } : {}),
      }) as RouteRecordRaw,
  );
}

function createTestRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: stubPages([...coreRoutes, fallbackNotFoundRoute]),
  });
  createRouterGuard(router);
  return router;
}

describe('后台路由菜单初始化', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.assign(mocks.accessStore, {
      accessCodes: [],
      accessMenus: [],
      accessRoutes: [],
      accessToken: 'token',
      isAccessChecked: false,
    });
    mocks.userStore.userInfo = null;
  });

  it.each([
    '/profile',
    '/notice-center?msgType=announcement#latest',
    '/system/user?keyword=a%26b%25%20c#details',
    '/missing-page',
  ])('刷新或新窗口直达 %s 时初始化菜单并保留地址', async (path) => {
    const router = createTestRouter();
    const target = router.resolve(path);
    await router.push(path);
    expect(router.currentRoute.value.path).toBe(target.path);
    expect(router.currentRoute.value.query).toEqual(target.query);
    expect(router.currentRoute.value.hash).toBe(target.hash);
    expect(mocks.fetchUserInfo).toHaveBeenCalledOnce();
    expect(mocks.getAccessCodes).toHaveBeenCalledOnce();
    expect(mocks.generateAccess).toHaveBeenCalledOnce();
    expect(mocks.accessStore.accessMenus).toHaveLength(1);
    expect(mocks.accessStore.isAccessChecked).toBe(true);
    if (path.startsWith('/system/user')) {
      expect(router.currentRoute.value.name).toBe('SystemUser');
      expect(router.currentRoute.value.query.keyword).toBe('a&b% c');
    }
    if (path === '/missing-page')
      expect(router.currentRoute.value.name).toBe('FallbackNotFoundView');
  });

  it.each(['/profile', '/notice-center?msgType=announcement', '/system/user'])(
    '未登录访问 %s 时跳登录并保留回跳地址',
    async (path) => {
      mocks.accessStore.accessToken = null;
      const router = createTestRouter();
      await router.push(path);
      expect(router.currentRoute.value.path).toBe('/auth/login');
      expect(router.currentRoute.value.query.redirect).toBe(
        encodeURIComponent(path),
      );
      expect(mocks.generateAccess).not.toHaveBeenCalled();
    },
  );

  it.each(['/auth/login', '/auth/forget-password'])(
    '公开页面 %s 不请求菜单',
    async (path) => {
      mocks.accessStore.accessToken = null;
      const router = createTestRouter();
      await router.push(path);
      expect(router.currentRoute.value.path).toBe(path);
      expect(mocks.generateAccess).not.toHaveBeenCalled();
    },
  );

  it('菜单加载完成前不进入页面，后续导航复用结果', async () => {
    const router = createTestRouter();
    const generate = mocks.generateAccess.getMockImplementation()!;
    let release!: () => void;
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    mocks.generateAccess.mockImplementationOnce(async (options) => {
      await pending;
      return generate(options);
    });
    const navigation = router.push('/profile');
    await vi.waitFor(() => expect(mocks.generateAccess).toHaveBeenCalledOnce());
    expect(router.currentRoute.value.path).not.toBe('/profile');
    expect(mocks.accessStore.isAccessChecked).toBe(false);
    release();
    await navigation;
    await router.push('/notice-center');
    expect(router.currentRoute.value.path).toBe('/notice-center');
    expect(mocks.generateAccess).toHaveBeenCalledOnce();
  });

  it('菜单失败不会标记完成，下一次导航可以重试', async () => {
    const router = createTestRouter();
    router.onError(() => {});
    mocks.generateAccess.mockRejectedValueOnce(new Error('menu unavailable'));
    await expect(router.push('/profile')).rejects.toThrow('menu unavailable');
    expect(mocks.accessStore.isAccessChecked).toBe(false);
    await router.push('/profile');
    expect(mocks.generateAccess).toHaveBeenCalledTimes(2);
    expect(mocks.accessStore.isAccessChecked).toBe(true);
  });

  it('已有登录态访问登录页时初始化菜单并回跳', async () => {
    const router = createTestRouter();
    const target = '/notice-center?msgType=announcement#latest';
    await router.push({
      path: '/auth/login',
      query: { redirect: encodeURIComponent(target) },
    });
    expect(router.currentRoute.value.fullPath).toBe(target);
    expect(mocks.generateAccess).toHaveBeenCalledOnce();
  });

  it('登录成功后返回原目标时只解码回跳参数一次', async () => {
    mocks.accessStore.accessToken = null;
    const router = createTestRouter();
    await router.push('/system/user?keyword=a%26b%25#details');
    expect(router.currentRoute.value.path).toBe('/auth/login');
    mocks.accessStore.accessToken = 'new-token';
    await router.push('/workspace');
    expect(router.currentRoute.value.name).toBe('SystemUser');
    expect(router.currentRoute.value.query).toEqual({ keyword: 'a&b%' });
    expect(router.currentRoute.value.hash).toBe('#details');
  });
});
