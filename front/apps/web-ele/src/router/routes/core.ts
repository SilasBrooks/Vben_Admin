import type { RouteRecordRaw } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';

import { $t } from '#/locales';

const BasicLayout = () => import('#/layouts/basic.vue');
const AuthPageLayout = () => import('#/layouts/auth.vue');
/** 全局404页面（包在 BasicLayout 内，保留侧边栏/头部，避免全屏 404 无法退出） */
const fallbackNotFoundRoute: RouteRecordRaw = {
  component: BasicLayout,
  meta: {
    hideInBreadcrumb: true,
    hideInMenu: true,
    hideInTab: true,
    title: '404',
  },
  name: 'FallbackNotFound',
  path: '/:path(.*)*',
  children: [
    {
      component: () => import('#/views/_core/fallback/not-found.vue'),
      meta: {
        hideInBreadcrumb: true,
        hideInMenu: true,
        hideInTab: true,
        title: '404',
      },
      name: 'FallbackNotFoundView',
      path: '',
    },
  ],
};

/** 基本路由，这些路由是必须存在的 */
const coreRoutes: RouteRecordRaw[] = [
  /**
   * 根路由
   * 使用基础布局，作为所有页面的父级容器，子级就不必配置BasicLayout。
   * 此路由必须存在，且不应修改
   */
  {
    component: BasicLayout,
    meta: {
      hideInBreadcrumb: true,
      title: 'Root',
    },
    name: 'Root',
    path: '/',
    redirect: preferences.app.defaultHomePath,
    children: [],
  },
  {
    component: BasicLayout,
    meta: {
      hideInBreadcrumb: true,
      hideInMenu: true,
      hideInTab: true,
      title: $t('page.auth.profile'),
    },
    name: 'ProfileParent',
    path: '/profile',
    children: [
      {
        component: () => import('#/views/_core/profile/index.vue'),
        meta: {
          hideInBreadcrumb: true,
          hideInMenu: true,
          title: $t('page.auth.profile'),
        },
        name: 'Profile',
        path: '',
      },
    ],
  },
  {
    component: BasicLayout,
    meta: {
      hideInBreadcrumb: true,
      hideInMenu: true,
      hideInTab: true,
      title: $t('notice.centerTitle'),
    },
    name: 'NoticeCenterParent',
    path: '/notice-center',
    children: [
      {
        component: () => import('#/views/notice/center/index.vue'),
        meta: {
          hideInBreadcrumb: true,
          hideInMenu: true,
          title: $t('notice.centerTitle'),
        },
        name: 'NoticeCenter',
        path: '',
      },
    ],
  },
  {
    component: AuthPageLayout,
    meta: {
      hideInTab: true,
      ignoreAccess: true,
      title: 'Authentication',
    },
    name: 'Authentication',
    path: '/auth',
    redirect: LOGIN_PATH,
    children: [
      {
        name: 'Login',
        path: 'login',
        component: () => import('#/views/_core/authentication/login.vue'),
        meta: {
          title: $t('page.auth.login'),
        },
      },
      {
        name: 'ForgetPassword',
        path: 'forget-password',
        component: () =>
          import('#/views/_core/authentication/password-recovery.vue'),
        meta: {
          title: $t('page.auth.forgetPassword'),
        },
      },
    ],
  },
];

export { coreRoutes, fallbackNotFoundRoute };
