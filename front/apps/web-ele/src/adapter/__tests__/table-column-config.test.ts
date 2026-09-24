import type { VxeTableGridOptions } from '@vben/plugins/vxe-table';

import { describe, expect, it, vi } from 'vitest';

import {
  createColumnConfigStorage,
  restoreColumnConfig,
  serializeColumnConfig,
} from '../table-column-config';

const columns = [
  { type: 'seq' as const },
  { field: 'name' },
  { field: 'status' },
  { field: '__actions', fixed: 'right' as const },
  { field: 'newColumn' },
];
type Custom = NonNullable<VxeTableGridOptions['customConfig']>;
type Update = Parameters<NonNullable<Custom['updateStore']>>[0];
const update = (visible: boolean, type: Update['type'] = 'confirm') =>
  ({
    $table: {} as Update['$table'],
    id: 'table.user',
    type,
    storeData: { sortData: [{ k: 'name' }], visibleData: { name: visible } },
  }) as Update;
const restore = {} as Parameters<NonNullable<Custom['restoreStore']>>[0];

function deferred() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

describe('个人列布局', () => {
  it('同一表格反复初始化只读取一次，保存后再读取最新配置', async () => {
    const first = deferred();
    const read = vi
      .fn()
      .mockImplementationOnce(async () => {
        await first.promise;
        return [{ field: 'name', visible: false }];
      })
      .mockResolvedValue([{ field: 'name', visible: true }]);
    const save = vi.fn().mockResolvedValue(undefined);
    const config = createColumnConfigStorage({
      currentUser: () => '1',
      read,
      save,
    })('table.user', columns);
    const initial = config.restoreStore!(restore);
    const repeated = config.restoreStore!(restore);
    expect(read).toHaveBeenCalledTimes(1);
    first.resolve();
    expect(await initial).toEqual(await repeated);
    expect(await config.restoreStore!(restore)).toEqual(await initial);
    expect(read).toHaveBeenCalledTimes(1);

    await config.updateStore!(update(true));
    expect((await config.restoreStore!(restore)).visibleData).toEqual({
      name: true,
    });
    expect(read).toHaveBeenCalledTimes(2);
  });

  it('往返保留列顺序、显隐、宽度、取消固定及序号和操作列', () => {
    const store = {
      sortData: [
        { k: 'type=seq' },
        { k: 'status' },
        { k: 'name' },
        { k: '__actions' },
      ],
      visibleData: { name: false },
      resizableData: { status: 180 },
      fixedData: { __actions: '' as const },
    };
    const value = serializeColumnConfig(store);
    expect(value.map((item) => item.field)).toEqual([
      'type=seq',
      'status',
      'name',
      '__actions',
    ]);
    expect(
      restoreColumnConfig(JSON.parse(JSON.stringify(value)), columns),
    ).toEqual(store);
  });

  it('忽略删除列、重复项和非法布局值，不覆盖新增列及业务定义', () => {
    const formatter = vi.fn();
    const definitions = [
      { field: 'name', title: '名称', formatter },
      ...columns.slice(2),
    ];
    const value = restoreColumnConfig(
      [
        { field: 'removed', visible: false },
        {
          field: 'name',
          width: -1,
          visible: 'false',
          fixed: 'invalid',
          title: '恶意标题',
        },
        { field: 'name', visible: false },
        { field: 'status', width: Number.POSITIVE_INFINITY },
        null,
        123,
      ],
      definitions,
    );
    expect(value).toEqual({
      sortData: [{ k: 'name' }, { k: 'status' }],
      fixedData: {},
      visibleData: {},
      resizableData: {},
    });
    expect(definitions[0]).toEqual({ field: 'name', title: '名称', formatter });
    expect(restoreColumnConfig('not an array', columns)).toEqual({});
    expect(restoreColumnConfig([], columns)).toEqual({});
  });

  it('按列表串行保存，重新挂载等待所有已排队的保存', async () => {
    const first = deferred();
    const save = vi
      .fn()
      .mockImplementationOnce(() => first.promise)
      .mockResolvedValue(undefined);
    const read = vi.fn().mockResolvedValue([{ field: 'name', visible: false }]);
    const factory = createColumnConfigStorage({
      currentUser: () => '1',
      read,
      save,
    });
    const config = factory('table.user', columns);
    const a = config.updateStore!(update(true));
    const b = config.updateStore!(update(false));
    const loaded = factory('table.user', columns).restoreStore!(restore);
    await Promise.resolve();
    expect(save).toHaveBeenCalledTimes(1);
    expect(read).not.toHaveBeenCalled();
    first.resolve();
    await Promise.all([a, b]);
    expect(await loaded).toMatchObject({ visibleData: { name: false } });
    expect(save.mock.calls.map((call) => call[1][0].visible)).toEqual([
      true,
      false,
    ]);
  });

  it('读取失败按默认布局处理，保存失败不阻塞后续保存，重置提交空数组', async () => {
    const save = vi
      .fn()
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValue(undefined);
    const read = vi.fn().mockRejectedValue(new Error('network'));
    const config = createColumnConfigStorage({
      currentUser: () => '1',
      read,
      save,
    })('table.user', columns);
    expect(await config.restoreStore!(restore)).toEqual({});
    await config.updateStore!(update(false));
    await config.updateStore!(update(true, 'reset'));
    expect(save).toHaveBeenLastCalledWith('table.user', []);
  });

  it('切换账号丢弃旧队列与旧读取结果，新用户和其他列表独立保存', async () => {
    let user = '1';
    const first = deferred();
    const reading = deferred();
    const save = vi
      .fn()
      .mockImplementationOnce(() => first.promise)
      .mockResolvedValue(undefined);
    const read = vi.fn().mockImplementation(async () => {
      await reading.promise;
      return [{ field: 'name', visible: false }];
    });
    const factory = createColumnConfigStorage({
      currentUser: () => user,
      read,
      save,
    });
    const oldRead = factory('table.role', columns).restoreStore!(restore);
    const a = factory('table.user', columns).updateStore!(update(false));
    const b = factory('table.user', columns).updateStore!(update(true));
    await Promise.resolve();
    user = '2';
    await factory('table.user', columns).updateStore!(update(true));
    await factory('table.role', columns).updateStore!(update(true));
    reading.resolve();
    first.resolve();
    await Promise.all([a, b]);
    expect(await oldRead).toEqual({});
    expect(save).toHaveBeenCalledTimes(3);
  });
});
