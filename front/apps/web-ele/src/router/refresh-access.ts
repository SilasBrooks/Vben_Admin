import type { Router } from 'vue-router';

import type { RouteRecordStringComponent } from '@vben/types';

import { nextTick, watch } from 'vue';

import { preferences } from '@vben/preferences';
import {
  getTabKey,
  useAccessStore,
  useTabbarStore,
  useUserStore,
} from '@vben/stores';

import { getAccessCodesApi, getUserConfigApi, getUserInfoApi } from '#/api';

import { prepareAccess } from './access';
import { accessibleHome, isAccessiblePage } from './access-routes';
import { accessRoutes } from './routes';

const pendingRefreshes = new WeakMap<Router, Promise<void>>();

/** 连续保存逐次读取最新配置，旧会话的结果不得写入新会话。 */
export function refreshAccess(router: Router): Promise<void> {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const tabbarStore = useTabbarStore();
  const token = accessStore.accessToken;
  const userId = userStore.userInfo?.id ?? userStore.userInfo?.userId;
  let stale = false;
  const stopSessionWatch = watch(
    [
      () => accessStore.accessToken,
      () => userStore.userInfo?.id ?? userStore.userInfo?.userId,
    ],
    ([currentToken, currentUserId]) => {
      if (!currentToken || currentUserId !== userId) stale = true;
    },
    { flush: 'sync' },
  );
  // 正常续签 token 不取消同步；退出登录（包括同账号重登）会使旧任务失效。
  const isCurrentSession = () =>
    !stale &&
    !!token &&
    !!accessStore.accessToken &&
    userId === (userStore.userInfo?.id ?? userStore.userInfo?.userId);

  const refresh = async () => {
    if (!isCurrentSession()) return;
    const [menus, codes, user] = await Promise.all([
      getUserConfigApi<RouteRecordStringComponent[]>('menu'),
      getAccessCodesApi(),
      getUserInfoApi(),
    ]);
    if (!isCurrentSession()) return;
    const prepared = await prepareAccess({
      router,
      roles: user.roles ?? [],
      routes: accessRoutes,
      fetchMenuListAsync: async () => menus,
    });
    if (!isCurrentSession()) return;

    const current = router.currentRoute.value;
    const changedPages = prepared.apply();
    userStore.setUserInfo(user);
    accessStore.setAccessCodes(codes);
    accessStore.setAccessRoutes(prepared.accessibleRoutes);
    accessStore.setIsAccessChecked(true);

    tabbarStore.tabs = tabbarStore.tabs.flatMap((tab) => {
      const path = tab.fullPath || tab.path;
      const resolved = router.resolve(path);
      if (
        !isAccessiblePage(router, path) ||
        resolved.name !== tab.name ||
        (tab.name && changedPages.has(tab.name))
      ) {
        tabbarStore.removeCachedRoute(tab.key ?? getTabKey(tab));
        return [];
      }
      return [
        {
          ...tab,
          ...resolved,
          name: tab.name,
          meta: {
            ...resolved.meta,
            affixTab: tab.meta.affixTab || resolved.meta.affixTab,
            newTabTitle: tab.meta.newTabTitle,
          },
        },
      ];
    });
    tabbarStore.visitHistory.retain(
      tabbarStore.tabs.map((tab) => tab.key ?? getTabKey(tab)),
    );
    await tabbarStore.updateCacheTabs();
    await nextTick();
    if (!isCurrentSession()) return;
    accessStore.setAccessMenus(prepared.accessibleMenus);

    const resolved = router.resolve(current.fullPath);
    const target =
      isAccessiblePage(router, current.fullPath) &&
      resolved.name === current.name
        ? current.fullPath
        : accessibleHome(
            router,
            user.homePath,
            preferences.app.defaultHomePath,
          );
    // 重匹配最新 meta；未变化的组件复用原实例，查询参数和锚点保持原样。
    const destination = router.resolve(target);
    await router.replace({
      path: destination.path,
      query: destination.query,
      hash: destination.hash,
      force: true,
    });
  };

  const task = (pendingRefreshes.get(router) ?? Promise.resolve())
    .catch(() => {})
    .then(refresh);
  pendingRefreshes.set(router, task);
  void task
    .finally(() => {
      stopSessionWatch();
      if (pendingRefreshes.get(router) === task)
        pendingRefreshes.delete(router);
    })
    .catch(() => {});
  return task;
}
