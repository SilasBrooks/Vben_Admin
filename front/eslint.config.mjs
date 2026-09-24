import { defineConfig } from '@vben/eslint-config';

export default defineConfig([
  {
    files: ['apps/web-ele/**/*.{js,jsx,ts,tsx,vue}'],
    rules: {
      '@typescript-eslint/no-invalid-void-type': 'off',
      'no-useless-assignment': 'off',
      'perfectionist/sort-imports': 'off',
      'perfectionist/sort-named-imports': 'off',
      'perfectionist/sort-object-types': 'off',
      'perfectionist/sort-switch-case': 'off',
      'perfectionist/sort-union-types': 'off',
      'unicorn/no-array-reduce': 'off',
      'unicorn/no-array-reverse': 'off',
      'unicorn/no-nested-ternary': 'off',
      'unicorn/no-useless-fallback-in-spread': 'off',
      'unicorn/prefer-add-event-listener': 'off',
      'unicorn/prefer-ternary': 'off',
      'vue/html-self-closing': 'off',
      'vue/multiline-html-element-content-newline': 'off',
    },
  },
  {
    files: ['packages/effects/request/src/request-client/request-client.ts'],
    rules: {
      'perfectionist/sort-classes': 'off',
    },
  },
  {
    files: ['pnpm-workspace.yaml'],
    rules: {
      'pnpm/yaml-no-unused-catalog-item': 'off',
    },
  },
]);
