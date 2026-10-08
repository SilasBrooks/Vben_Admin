import { defineConfig } from '@vben/vite-config';

import ElementPlus from 'unplugin-element-plus/vite';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      plugins: [
        ElementPlus({
          format: 'esm',
        }),
      ],
      server: {
        proxy: {
          '/api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, ''),
            // 后端服务（service/，context-path=/api）；如需切回官方 mock 改为
            // http://localhost:5320/api 并将 .env.development 的 VITE_NITRO_MOCK 置为 true
            target: 'http://localhost:8080/api',
            ws: true,
          },
        },
        // 预热所有页面与核心目录（递归）：默认框架配置的浅层 glob 只覆盖一级目录，
        // src/views 下的页面从未被预热，导致每个页面首次跳转都要现场编译依赖链（白屏转圈）。
        // 服务启动后后台预热，首次跳转直接命中缓存。
        warmup: {
          clientFiles: [
            './src/{views,layouts,router,store,api,adapter,locales}/**/*.{vue,ts,tsx}',
          ],
        },
      },
    },
  };
});
