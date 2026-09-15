import type { Ref } from 'vue';

import { ref } from 'vue';

import { getDictOptionsApi } from '#/api/system/dict';

export interface DictOption {
  label: string;
  value: string;
}

interface DictEntry {
  /** 响应式选项，异步填充后所有使用处自动更新 */
  options: Ref<DictOption[]>;
  /** 是否已发起过加载（防重复请求） */
  loaded: boolean;
}

/**
 * 字典选项 hook：`const { options } = useDict('wsm_stock_status')`
 *
 * - 模块级缓存：同一 dictType 会话内只请求一次
 * - options 是响应式数组，模板可直接 `v-for="opt in options"`，
 *   脚本内取值用 `options.value`
 * - 字典管理页保存后调用 clearDictCache，所有已打开页面的选项自动刷新
 */
const cache = new Map<string, DictEntry>();

function load(dictType: string, entry: DictEntry) {
  entry.loaded = true;
  getDictOptionsApi(dictType)
    .then((items) => {
      entry.options.value = items.map((item) => ({
        label: item.dictLabel,
        value: item.dictValue,
      }));
    })
    .catch(() => {
      // 加载失败允许下次重试
      entry.loaded = false;
    });
}

export function useDict(dictType: string) {
  let entry = cache.get(dictType);
  if (!entry) {
    entry = { options: ref<DictOption[]>([]), loaded: false };
    cache.set(dictType, entry);
  }
  if (!entry.loaded) {
    load(dictType, entry);
  }
  return { options: entry.options };
}

/** 清空字典缓存（字典管理页保存后调用，保证其他页面拿到新值） */
export function clearDictCache(dictType?: string) {
  const reset = (key: string, entry: DictEntry) => {
    entry.options.value = [];
    entry.loaded = false;
    load(key, entry);
  };
  if (dictType) {
    const entry = cache.get(dictType);
    if (entry) {
      reset(dictType, entry);
    }
  } else {
    cache.forEach((entry, key) => reset(key, entry));
  }
}
