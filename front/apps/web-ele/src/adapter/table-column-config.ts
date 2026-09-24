import type { VxeTableGridOptions } from '@vben/plugins/vxe-table';

import type { TableColumnConfig } from '#/api/core/user-config';

type CustomConfig = NonNullable<VxeTableGridOptions['customConfig']>;
type StoreData = Parameters<
  NonNullable<CustomConfig['updateStore']>
>[0]['storeData'];
// Vben 的配置允许深层 partial；这里只需要列标识及分组关系。
interface ColumnDefinition {
  field?: string;
  type?: null | string;
  children?: Columns;
}
type Columns = (ColumnDefinition | undefined)[];

/** 只保存布局差异，标题、插槽、formatter 等仍由页面列定义提供。 */
export function serializeColumnConfig(store: StoreData): TableColumnConfig[] {
  const toColumns = (
    sorted: NonNullable<StoreData['sortData']>,
  ): TableColumnConfig[] =>
    sorted.map(({ k: field, c }) => ({
      field,
      ...(typeof store.visibleData?.[field] === 'boolean'
        ? { visible: store.visibleData[field] }
        : {}),
      ...(store.resizableData?.[field]
        ? { width: store.resizableData[field] }
        : {}),
      ...(store.fixedData && Object.hasOwn(store.fixedData, field)
        ? { fixed: store.fixedData[field] || '' }
        : {}),
      ...(c?.length ? { children: toColumns(c) } : {}),
    }));
  return toColumns(store.sortData ?? []);
}

export function restoreColumnConfig(
  value: unknown,
  columns: Columns,
): StoreData {
  const store: StoreData = {
    fixedData: {},
    resizableData: {},
    sortData: [],
    visibleData: {},
  };
  const seen = new Set<string>();
  const restore = (
    items: unknown,
    definitions: Columns,
  ): NonNullable<StoreData['sortData']> => {
    if (!Array.isArray(items)) return [];
    const available = new Map(
      definitions
        .filter((column) => !!column)
        .map((column) => [
          column.field || (column.type ? `type=${column.type}` : ''),
          column,
        ]),
    );
    return items.flatMap((item: unknown) => {
      if (!item || typeof item !== 'object' || !('field' in item)) return [];
      const config = item as TableColumnConfig;
      const column = available.get(config.field);
      if (!column || seen.has(config.field)) return [];
      seen.add(config.field);
      if (typeof config.visible === 'boolean')
        store.visibleData![config.field] = config.visible;
      if (
        typeof config.width === 'number' &&
        Number.isFinite(config.width) &&
        config.width > 0
      ) {
        store.resizableData![config.field] = config.width;
      }
      if (
        config.fixed === '' ||
        config.fixed === 'left' ||
        config.fixed === 'right'
      ) {
        store.fixedData![config.field] = config.fixed;
      }
      return [
        {
          k: config.field,
          ...(column.children?.length
            ? { c: restore(config.children, column.children) }
            : {}),
        },
      ];
    });
  };
  store.sortData = restore(value, columns);
  return store.sortData.length ? store : {};
}

interface StorageDependencies {
  currentUser: () => string | undefined;
  read: (key: string) => Promise<unknown>;
  save: (key: string, value: TableColumnConfig[]) => Promise<unknown>;
}

/** 跨组件挂载共享待保存任务，确保旧请求不会最后覆盖最新的布局。 */
export function createColumnConfigStorage(dependencies: StorageDependencies) {
  const pending = new Map<string, Promise<void>>();
  return (key: string, columns: Columns): CustomConfig => {
    let restored: { user: string; value: Promise<StoreData> } | undefined;
    return {
      enabled: true,
      storage: true,
      storeOptions: { visible: true, resizable: true, sort: true, fixed: true },
      restoreStore() {
        const user = dependencies.currentUser();
        if (!user) return Promise.resolve({});
        if (restored?.user === user) return restored.value;
        const value = (async (): Promise<StoreData> => {
          const queueKey = `${user}:${key}`;
          while (pending.has(queueKey) && dependencies.currentUser() === user) {
            await pending.get(queueKey);
          }
          if (dependencies.currentUser() !== user) return {};
          try {
            const config = await dependencies.read(key);
            return dependencies.currentUser() === user
              ? restoreColumnConfig(config, columns)
              : {};
          } catch {
            // 请求层已提示；配置失败不阻断业务列表渲染。
            return {};
          }
        })();
        restored = { user, value };
        return value;
      },
      updateStore({ type, storeData }) {
        restored = undefined;
        const user = dependencies.currentUser();
        if (!user) return Promise.resolve();
        const queueKey = `${user}:${key}`;
        const value = type === 'reset' ? [] : serializeColumnConfig(storeData);
        const task = (pending.get(queueKey) ?? Promise.resolve())
          .then(async () => {
            if (dependencies.currentUser() === user)
              await dependencies.save(key, value);
          })
          .catch(() => {
            // 请求层显示失败，继续允许后续布局保存，避免 VXE 产生未处理的拒绝。
          })
          .finally(() => {
            if (pending.get(queueKey) === task) pending.delete(queueKey);
          });
        pending.set(queueKey, task);
        return task;
      },
    };
  };
}
