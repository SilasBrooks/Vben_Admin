import type {
  ComponentRecordType,
  GenerateMenuAndRoutesOptions,
  RouteRecordStringComponent,
} from '@vben/types';

import { createMemoryHistory, createRouter } from 'vue-router';

import { generateAccessible } from '@vben/access';
import { preferences } from '@vben/preferences';
import { cloneDeep } from '@vben/utils';

import { ElMessage } from 'element-plus';

import { getUserConfigApi } from '#/api';
import { BasicLayout, IFrameView } from '#/layouts';
import { $t } from '#/locales';

import { menuComponentKeys, replaceAccessRoutes } from './access-routes';
import { routes } from './routes';

const forbiddenComponent = () => import('#/views/_core/fallback/forbidden.vue');

async function prepareAccess(options: GenerateMenuAndRoutesOptions) {
  const pageMap: ComponentRecordType = import.meta.glob('../views/**/*.vue');

  const layoutMap: ComponentRecordType = {
    BasicLayout,
    IFrameView,
  };

  const menus = options.fetchMenuListAsync
    ? await options.fetchMenuListAsync()
    : await loadMenus();
  const componentKeys = menuComponentKeys(menus);
  // 转换成功后才修改当前路由，避免请求失败时丢失现有页面。
  const preparedRouter = createRouter({
    history: createMemoryHistory(),
    routes: cloneDeep(routes),
  });
  const result = await generateAccessible(preferences.app.accessMode, {
    ...options,
    router: preparedRouter,
    fetchMenuListAsync: async () => menus,
    // 可以指定没有权限跳转403页面
    forbiddenComponent,
    // 如果 route.meta.menuVisibleWithForbidden = true
    layoutMap: { ...layoutMap, ...options.layoutMap },
    pageMap: options.pageMap ?? pageMap,
  });
  return {
    ...result,
    apply: () =>
      replaceAccessRoutes(
        options.router,
        preparedRouter,
        result.accessibleRoutes,
        routes,
        componentKeys,
      ),
  };
}

async function loadMenus() {
  ElMessage({ duration: 1500, message: `${$t('common.loadingMenu')}...` });
  return getUserConfigApi<RouteRecordStringComponent[]>('menu');
}

async function generateAccess(options: GenerateMenuAndRoutesOptions) {
  const prepared = await prepareAccess(options);
  prepared.apply();
  return prepared;
}

export { generateAccess, prepareAccess };
