import { defineConfig } from '@vben/oxlint-config';

export default defineConfig({
  overrides: [
    {
      files: ['apps/web-ele/**/*.ts', 'apps/web-ele/**/*.vue'],
      rules: {
        'eslint/eqeqeq': 'off',
        'import/consistent-type-specifier-style': 'off',
        'typescript/no-non-null-assertion': 'off',
        'unicorn/no-array-reverse': 'off',
        'unicorn/no-useless-fallback-in-spread': 'off',
        'unicorn/prefer-add-event-listener': 'off',
      },
    },
  ],
});
