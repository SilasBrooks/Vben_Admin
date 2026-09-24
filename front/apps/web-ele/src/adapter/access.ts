import type { App, DirectiveBinding, ShallowRef, WatchStopHandle } from 'vue';

import { shallowRef, watchEffect } from 'vue';

import { useAccess } from '@vben/access';

import './access.css';

type AccessBinding = Pick<DirectiveBinding<string | string[]>, 'arg' | 'value'>;

/** 框架指令只在挂载时检查；业务菜单支持运行时更新，需保留元素以恢复权限。 */
export function registerReactiveAccessDirective(app: App) {
  const states = new WeakMap<
    HTMLElement,
    {
      binding: ShallowRef<AccessBinding>;
      stop: WatchStopHandle;
    }
  >();
  app.directive('access', {
    mounted(el: HTMLElement, value: AccessBinding) {
      const binding = shallowRef<AccessBinding>({
        arg: value.arg,
        value: value.value,
      });
      const { accessMode, hasAccessByCodes, hasAccessByRoles } = useAccess();
      const stop = watchEffect(() => {
        const { arg, value: codes } = binding.value;
        const check =
          accessMode.value === 'frontend' && arg === 'role'
            ? hasAccessByRoles
            : hasAccessByCodes;
        const allowed = !codes || check(Array.isArray(codes) ? codes : [codes]);
        el.toggleAttribute('data-access-denied', !allowed);
      });
      states.set(el, { binding, stop });
    },
    updated(el: HTMLElement, binding: AccessBinding) {
      const state = states.get(el);
      if (state)
        state.binding.value = { arg: binding.arg, value: binding.value };
    },
    unmounted(el: HTMLElement) {
      states.get(el)?.stop();
      states.delete(el);
    },
  });
}
