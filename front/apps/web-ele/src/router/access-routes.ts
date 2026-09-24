import type { RouteRecordName, RouteRecordRaw, Router } from 'vue-router';

import type { RouteRecordStringComponent } from '@vben/types';

const installedComponents = new WeakMap<Router, Map<RouteRecordName, string>>();

export function menuComponentKeys(menus: RouteRecordStringComponent[]) {
  const keys = new Map<RouteRecordName, string>();
  const visit = (nodes: RouteRecordStringComponent[]) => {
    for (const node of nodes) {
      if (node.name) {
        keys.set(
          node.name,
          JSON.stringify([
            node.component,
            !!node.meta?.keepAlive,
            !!node.meta?.menuVisibleWithForbidden,
            node.meta?.iframeSrc,
            !!node.meta?.domCached,
          ]),
        );
      }
      if (node.children) visit(node.children);
    }
  };
  visit(menus);
  return keys;
}

/** 只替换动态记录，基础布局不重建；未改变的页面复用已解析组件。 */
export function replaceAccessRoutes(
  router: Router,
  preparedRouter: Router,
  dynamicRoutes: RouteRecordRaw[],
  staticRoutes: RouteRecordRaw[],
  componentKeys: Map<RouteRecordName, string>,
) {
  const previousKeys = installedComponents.get(router);
  const previousRoutes = new Map(
    router.getRoutes().map((route) => [route.name, route]),
  );
  const nextRoutes = new Map(
    preparedRouter.getRoutes().map((route) => [route.name, route]),
  );
  const changedPages = new Set<RouteRecordName>();
  const staticNames = new Set<RouteRecordName>();
  const collectStatic = (nodes: RouteRecordRaw[]) => {
    for (const node of nodes) {
      if (node.name) staticNames.add(node.name);
      if (node.children) collectStatic(node.children);
    }
  };
  collectStatic(staticRoutes);

  const reuseComponents = (nodes: RouteRecordRaw[]) => {
    for (const route of nodes) {
      const name = route.name;
      const previous = name && previousRoutes.get(name);
      const next = name && nextRoutes.get(name);
      if (name && previous && next) {
        if (
          previous.path === next.path &&
          componentKeys.has(name) &&
          previousKeys?.get(name) === componentKeys.get(name)
        ) {
          if (route.component && previous.components?.default) {
            route.component = previous.components.default;
          }
        } else {
          changedPages.add(name);
        }
      }
      if (route.children) reuseComponents(route.children);
    }
  };
  reuseComponents(dynamicRoutes);

  for (const name of previousRoutes.keys()) {
    if (name && !staticNames.has(name)) router.removeRoute(name);
  }
  for (const route of dynamicRoutes) {
    if (router.hasRoute('Root') && !route.meta?.noBasicLayout) {
      router.addRoute('Root', route);
    } else {
      router.addRoute(route);
    }
  }
  installedComponents.set(router, componentKeys);
  return changedPages;
}

/** 不能仅判断 matched 非空：未知地址也会匹配全局 404。 */
export function isAccessiblePage(router: Router, path: string) {
  const resolved = router.resolve(path);
  return (
    resolved.matched.length > 0 &&
    !resolved.matched.some(
      (record) =>
        record.name === 'FallbackNotFound' ||
        record.name === 'FallbackNotFoundView' ||
        record.meta.ignoreAccess ||
        record.meta.menuVisibleWithForbidden,
    )
  );
}

export function accessibleHome(
  router: Router,
  ...paths: (string | undefined)[]
) {
  for (const path of paths) {
    if (!path || !path.startsWith('/') || path.startsWith('//')) continue;
    // 首页目录可能还会 redirect 到已删除的菜单，逐层校验并阻止循环。
    let candidate = path;
    const visited = new Set<string>();
    while (isAccessiblePage(router, candidate) && !visited.has(candidate)) {
      visited.add(candidate);
      const resolved = router.resolve(candidate);
      const redirect = resolved.matched.at(-1)?.redirect;
      if (!redirect) return candidate;
      const target =
        typeof redirect === 'function'
          ? redirect(resolved, router.currentRoute.value)
          : redirect;
      candidate = router.resolve(target).fullPath;
    }
  }
  return '/profile';
}
