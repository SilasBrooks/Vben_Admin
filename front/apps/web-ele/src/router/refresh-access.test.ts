import type { RouteRecordRaw, Router } from 'vue-router';

import type {
  GenerateMenuAndRoutesOptions,
  RouteRecordStringComponent,
} from '@vben/types';

import {
  createApp,
  defineComponent,
  h,
  nextTick,
  onMounted,
  reactive,
  ref,
} from 'vue';
import { createMemoryHistory, createRouter, RouterView } from 'vue-router';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { prepareAccess } from './access';
import { accessibleHome } from './access-routes';
import { refreshAccess } from './refresh-access';

const mocks = vi.hoisted(() => ({
  menus: [] as RouteRecordStringComponent[],
  staticRoutes: [] as RouteRecordRaw[],
  pages: {} as NonNullable<GenerateMenuAndRoutesOptions['pageMap']>,
  access: {} as any,
  user: {} as any,
  tabs: {} as any,
  getMenus: vi.fn(),
  getCodes: vi.fn(),
  getUser: vi.fn(),
}));

vi.mock('#/api', () => ({
  getUserConfigApi: mocks.getMenus,
  getAccessCodesApi: mocks.getCodes,
  getUserInfoApi: mocks.getUser,
}));
vi.mock('#/locales', () => ({ $t: (key: string) => key }));
vi.mock('#/layouts', () => ({ BasicLayout: {}, IFrameView: {} }));
vi.mock('@vben/preferences', () => ({
  preferences: { app: { accessMode: 'backend', defaultHomePath: '/home' } },
}));
vi.mock('@vben/stores', () => ({
  useAccessStore: () => mocks.access,
  useUserStore: () => mocks.user,
  useTabbarStore: () => mocks.tabs,
  getTabKey: (tab: { fullPath: string; path: string }) =>
    tab.fullPath || tab.path,
}));
vi.mock('./routes', () => ({ routes: mocks.staticRoutes, accessRoutes: [] }));
vi.mock('./access', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./access')>();
  return {
    ...actual,
    prepareAccess: (options: GenerateMenuAndRoutesOptions) =>
      actual.prepareAccess({ ...options, pageMap: mocks.pages }),
  };
});

let app: ReturnType<typeof createApp> | undefined;
let container: HTMLDivElement;
let mounts: number;

function menus(title = 'Original'): RouteRecordStringComponent[] {
  return [
    {
      name: 'Home',
      path: '/home',
      component: '/home',
      meta: { title: 'Home' },
    },
    {
      name: 'System',
      path: '/system',
      component: '',
      meta: { title: 'System' },
      children: [
        {
          name: 'Menu',
          path: '/system/menu',
          component: '/menu',
          meta: { title, keepAlive: true },
        },
        {
          name: 'Old',
          path: '/system/old',
          component: '/old',
          meta: { title: 'Old' },
        },
      ],
    },
  ];
}

async function setupRouter(path = '/system/menu?keyword=a%26b%25#details') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: mocks.staticRoutes,
  });
  const prepared = await prepareAccess({
    router,
    routes: [],
    fetchMenuListAsync: async () => structuredClone(mocks.menus),
  });
  prepared.apply();
  mocks.access.accessMenus = prepared.accessibleMenus;
  mocks.access.accessRoutes = prepared.accessibleRoutes;
  await router.push(path);
  const current = router.currentRoute.value;
  mocks.tabs.tabs.push({ ...current, key: current.fullPath });
  return router;
}

function mountRouter(router: Router) {
  app = createApp({ render: () => h(RouterView) });
  app.use(router);
  app.mount(container);
}

beforeEach(() => {
  vi.clearAllMocks();
  mounts = 0;
  container = document.createElement('div');
  document.body.append(container);
  const Shell = defineComponent({ render: () => h(RouterView) });
  const Page = defineComponent({
    setup() {
      const value = ref('');
      onMounted(() => mounts++);
      return () =>
        h('input', {
          value: value.value,
          onInput: (event: Event) => {
            value.value = (event.target as HTMLInputElement).value;
          },
        });
    },
  });
  mocks.pages = {
    '/menu.vue': async () => ({ default: Page }),
    '/home.vue': async () => ({ default: Page }),
    '/old.vue': async () => ({ default: Page }),
    '/other.vue': async () => ({
      default: defineComponent({ render: () => h('p', 'Other page') }),
    }),
  } as GenerateMenuAndRoutesOptions['pageMap'] & {};
  mocks.staticRoutes.splice(
    0,
    mocks.staticRoutes.length,
    {
      name: 'Root',
      path: '/',
      component: Shell,
      children: [],
      redirect: '/home',
    },
    { name: 'Profile', path: '/profile', component: Page },
    {
      name: 'Login',
      path: '/login',
      component: Page,
      meta: { title: 'Login', ignoreAccess: true },
    },
    { name: 'FallbackNotFound', path: '/:path(.*)*', component: Page },
  );
  mocks.menus = menus();
  mocks.access = reactive({
    accessToken: 'token-a',
    accessCodes: ['old'],
    accessRoutes: [],
    accessMenus: [],
    isAccessChecked: true,
    setAccessCodes(value: string[]) {
      this.accessCodes = value;
    },
    setAccessRoutes(value: any) {
      this.accessRoutes = value;
    },
    setAccessMenus(value: any) {
      this.accessMenus = value;
    },
    setIsAccessChecked(value: boolean) {
      this.isAccessChecked = value;
    },
  });
  mocks.user = reactive({
    userInfo: { id: 1, userId: '1', roles: ['admin'], homePath: '/home' },
    setUserInfo(value: any) {
      this.userInfo = value;
    },
  });
  mocks.tabs = reactive({
    tabs: [] as any[],
    visitHistory: { retain: vi.fn() },
    removeCachedRoute: vi.fn(),
    updateCacheTabs: vi.fn(async () => {}),
  });
  mocks.getMenus.mockImplementation(async () => structuredClone(mocks.menus));
  mocks.getCodes.mockResolvedValue(['System:Menu:Edit']);
  mocks.getUser.mockImplementation(async () => ({ ...mocks.user.userInfo }));
});

afterEach(() => {
  app?.unmount();
  app = undefined;
  container.remove();
});

describe('菜单应用内同步', () => {
  it('保留页面实例、输入、查询参数和锚点，更新标题并清除旧路由', async () => {
    const router = await setupRouter();
    mountRouter(router);
    const input = container.querySelector('input')!;
    input.value = 'Unsaved draft';
    input.dispatchEvent(new Event('input'));
    await nextTick();
    const rootComponent = router.getRoutes().find((r) => r.name === 'Root')
      ?.components?.default;
    mocks.menus = menus('Updated');
    mocks.menus[1]!.children!.splice(1, 1);
    await refreshAccess(router);
    expect(router.currentRoute.value.fullPath).toBe(
      '/system/menu?keyword=a%26b%25#details',
    );
    expect(router.currentRoute.value.meta.title).toBe('Updated');
    expect(container.querySelector('input')).toBe(input);
    expect(input.value).toBe('Unsaved draft');
    expect(mounts).toBe(1);
    expect(
      router.getRoutes().find((r) => r.name === 'Root')?.components?.default,
    ).toBe(rootComponent);
    expect(router.hasRoute('Old')).toBe(false);
    expect(mocks.tabs.tabs[0].meta.title).toBe('Updated');
    expect(mocks.access.accessCodes).toEqual(['System:Menu:Edit']);
    // 再同步一次，验证静态路由模板未被历史动态路由污染。
    await refreshAccess(router);
    expect(router.hasRoute('Old')).toBe(false);
    expect(router.getRoutes().filter((r) => r.name === 'Menu')).toHaveLength(1);
    expect(mounts).toBe(1);
  });

  it('当前菜单删除且首页不可用时回到个人中心，清理固定标签和缓存', async () => {
    const router = await setupRouter();
    mocks.tabs.tabs[0].meta.affixTab = true;
    mocks.menus = [];
    await refreshAccess(router);
    expect(router.currentRoute.value.path).toBe('/profile');
    expect(router.hasRoute('Menu')).toBe(false);
    expect(mocks.tabs.tabs).toEqual([]);
    expect(mocks.tabs.removeCachedRoute).toHaveBeenCalledWith(
      '/system/menu?keyword=a%26b%25#details',
    );
  });

  it('新增菜单可以导航，组件变化时切换到新页面', async () => {
    const router = await setupRouter();
    mountRouter(router);
    mocks.menus[1]!.children![0]!.component = '/other';
    mocks.menus.push({
      name: 'New',
      path: '/new',
      component: '/other',
      meta: { title: 'New' },
    });
    await refreshAccess(router);
    expect(container.textContent).toContain('Other page');
    expect(mocks.tabs.removeCachedRoute).toHaveBeenCalled();
    await router.push('/new');
    expect(router.currentRoute.value.name).toBe('New');
  });

  it('同步失败保留旧路由和权限，之后可重试', async () => {
    const router = await setupRouter();
    const route = router.getRoutes().find((r) => r.name === 'Menu');
    mocks.getCodes.mockRejectedValueOnce(new Error('offline'));
    mocks.menus = menus('Retried');
    await expect(refreshAccess(router)).rejects.toThrow('offline');
    expect(router.getRoutes().find((r) => r.name === 'Menu')).toBe(route);
    expect(mocks.access.accessCodes).toEqual(['old']);
    await refreshAccess(router);
    expect(router.currentRoute.value.meta.title).toBe('Retried');
  });

  it('连续同步串行读取，后一次修改不会被旧结果覆盖', async () => {
    const router = await setupRouter();
    let release!: (value: RouteRecordStringComponent[]) => void;
    mocks.getMenus.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          release = resolve;
        }),
    );
    const first = refreshAccess(router);
    const second = refreshAccess(router);
    await vi.waitFor(() => expect(mocks.getMenus).toHaveBeenCalledTimes(1));
    mocks.menus = menus('Newest');
    release(menus('Older'));
    await Promise.all([first, second]);
    expect(mocks.getMenus).toHaveBeenCalledTimes(2);
    expect(router.currentRoute.value.meta.title).toBe('Newest');
  });

  it('切换账号后丢弃旧请求和排队任务', async () => {
    const router = await setupRouter();
    let release!: (value: RouteRecordStringComponent[]) => void;
    mocks.getMenus.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          release = resolve;
        }),
    );
    const first = refreshAccess(router);
    const second = refreshAccess(router);
    await vi.waitFor(() => expect(mocks.getMenus).toHaveBeenCalledOnce());
    mocks.access.accessToken = 'token-b';
    mocks.user.userInfo.id = 2;
    mocks.access.accessCodes = ['account-b'];
    release(menus('Account A'));
    await Promise.all([first, second]);
    expect(mocks.getMenus).toHaveBeenCalledOnce();
    expect(mocks.access.accessCodes).toEqual(['account-b']);
    expect(router.currentRoute.value.meta.title).toBe('Original');
  });

  it('回退首页不会进入无效重定向或重定向循环', async () => {
    const router = await setupRouter();
    router.addRoute({ name: 'Cycle', path: '/cycle', redirect: '/cycle' });
    router.addRoute({
      name: 'MissingRedirect',
      path: '/bad-home',
      redirect: '/missing',
    });
    expect(accessibleHome(router, '/cycle', '/bad-home', '/login')).toBe(
      '/profile',
    );
    expect(accessibleHome(router, '/home')).toBe('/home');
  });

  it('正常 token 续签仍完成同步，同账号退出重登则丢弃旧结果', async () => {
    const router = await setupRouter();
    let release!: (value: RouteRecordStringComponent[]) => void;
    mocks.getMenus.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          release = resolve;
        }),
    );
    const refresh = refreshAccess(router);
    await vi.waitFor(() => expect(mocks.getMenus).toHaveBeenCalledOnce());
    mocks.access.accessToken = 'renewed-token';
    release(menus('Renewed'));
    await refresh;
    expect(router.currentRoute.value.meta.title).toBe('Renewed');

    mocks.getMenus.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          release = resolve;
        }),
    );
    const staleRefresh = refreshAccess(router);
    await vi.waitFor(() => expect(mocks.getMenus).toHaveBeenCalledTimes(2));
    mocks.access.accessToken = null;
    mocks.access.accessToken = 'new-session';
    release(menus('Stale'));
    await staleRefresh;
    expect(router.currentRoute.value.meta.title).toBe('Renewed');
  });
});
