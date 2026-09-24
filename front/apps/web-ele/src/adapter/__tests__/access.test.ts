import {
  createApp,
  h,
  nextTick,
  ref,
  resolveDirective,
  withDirectives,
} from 'vue';

import { describe, expect, it, vi } from 'vitest';

import { registerReactiveAccessDirective } from '../access';

const state = vi.hoisted(() => ({ codes: undefined as any }));
vi.mock('@vben/access', () => ({
  useAccess: () => ({
    accessMode: { value: 'backend' },
    hasAccessByCodes: (codes: string[]) =>
      codes.some((code) => state.codes.value.includes(code)),
    hasAccessByRoles: () => false,
  }),
}));

describe('动态按钮权限', () => {
  it('权限撤销与恢复不销毁按钮，并响应指令绑定变化', async () => {
    state.codes = ref<string[]>([]);
    const permission = ref('System:Menu:Add');
    const root = document.createElement('div');
    const app = createApp({
      setup: () => () =>
        withDirectives(h('button', 'Add'), [
          [resolveDirective('access')!, permission.value, 'code'],
        ]),
    });
    registerReactiveAccessDirective(app);
    app.mount(root);
    try {
      const button = root.querySelector('button')!;
      expect(button.hasAttribute('data-access-denied')).toBe(true);
      state.codes.value = ['System:Menu:Add'];
      await nextTick();
      expect(button.hasAttribute('data-access-denied')).toBe(false);
      permission.value = 'System:Menu:Edit';
      await nextTick();
      expect(button.hasAttribute('data-access-denied')).toBe(true);
      state.codes.value = ['System:Menu:Edit'];
      await nextTick();
      expect(root.querySelector('button')).toBe(button);
      expect(button.hasAttribute('data-access-denied')).toBe(false);
      state.codes.value = [];
      await nextTick();
      expect(button.hasAttribute('data-access-denied')).toBe(true);
    } finally {
      app.unmount();
    }
  });
});
