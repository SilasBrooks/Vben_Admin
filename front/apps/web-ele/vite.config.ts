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
      },
    },
  };
});
