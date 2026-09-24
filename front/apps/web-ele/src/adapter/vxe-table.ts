import type { VxeTableGridOptions } from '@vben/plugins/vxe-table';

import type { ComponentPropsMap, ComponentType } from './component';

import { h } from 'vue';

import {
  setupVbenVxeTable,
  useVbenVxeGrid as useGrid,
} from '@vben/plugins/vxe-table';
import { useUserStore } from '@vben/stores';

import { ElButton, ElImage } from 'element-plus';

import { getUserConfigApi, saveUserConfigApi } from '#/api/core/user-config';

import { useVbenForm } from './form';
import { createColumnConfigStorage } from './table-column-config';

const columnConfigStorage = createColumnConfigStorage({
  currentUser: () => {
    const user = useUserStore().userInfo;
    const id = user?.id ?? user?.userId;
    return id == null ? undefined : String(id);
  },
  read: getUserConfigApi,
  save: saveUserConfigApi,
});

setupVbenVxeTable({
  configVxeTable: (vxeUI) => {
    vxeUI.setConfig({
      grid: {
        align: 'center',
        border: false,
        columnConfig: {
          resizable: true,
        },
        minHeight: 180,
        formConfig: {
          // 全局禁用vxe-table的表单配置，使用formOptions
          enabled: false,
        },
        proxyConfig: {
          autoLoad: true,
          response: {
            result: 'items',
            total: 'total',
            list: 'items',
          },
          showActiveMsg: true,
          showResponseMsg: false,
        },
        round: true,
        showOverflow: true,
        size: 'small',
      } as VxeTableGridOptions,
    });

    // 表格配置项可以用 cellRender: { name: 'CellImage' },
    vxeUI.renderer.add('CellImage', {
      renderTableDefault(renderOpts, params) {
        const { props } = renderOpts;
        const { column, row } = params;
        const src = row[column.field];
        return h(ElImage, { src, previewSrcList: [src], ...props });
      },
    });

    // 表格配置项可以用 cellRender: { name: 'CellLink' },
    vxeUI.renderer.add('CellLink', {
      renderTableDefault(renderOpts) {
        const { props } = renderOpts;
        return h(
          ElButton,
          { size: 'small', link: true },
          { default: () => props?.text },
        );
      },
    });

    // 这里可以自行扩展 vxe-table 的全局配置，比如自定义格式化
    // vxeUI.formats.add
  },
  useVbenForm,
});

export const useVbenVxeGrid = <T extends Record<string, any>>(
  ...rest: Parameters<typeof useGrid<T, ComponentType, ComponentPropsMap>>
) => {
  const options = rest[0];
  const gridOptions = options?.gridOptions;
  if (gridOptions?.toolbarConfig?.custom) {
    if (typeof gridOptions.id !== 'string' || !gridOptions.id) {
      throw new Error('Customizable grids require a stable gridOptions.id');
    }
    gridOptions.customConfig = {
      ...gridOptions.customConfig,
      ...columnConfigStorage(gridOptions.id, gridOptions.columns ?? []),
    };
  }
  if (options?.formOptions) {
    // 搜索字段不超过一行时"展开/收起"按钮无实际作用，默认关闭；需要时可在页面 formOptions 显式开启
    options.formOptions = {
      showCollapseButton: false,
      ...options.formOptions,
    };
  }
  return useGrid<T, ComponentType, ComponentPropsMap>(...rest);
};

export type * from '@vben/plugins/vxe-table';
