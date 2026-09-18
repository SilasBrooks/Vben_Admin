<script lang="ts" setup>
import { ref } from 'vue';

import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { useDict } from '#/hooks/use-dict';

interface StockItem {
  id: number;
  sku: string;
  name: string;
  quantity: number;
  status: number;
}

/** 字典实例：库存状态下拉与表格标签均由 wsm_stock_status 字典驱动 */
const { options: statusOptions } = useDict('wsm_stock_status');

/** 字典值 -> el-tag 类型（正常绿 / 缺货红 / 预警黄） */
const statusTagType: Record<string, 'success' | 'danger' | 'warning'> = {
  0: 'success',
  1: 'danger',
  2: 'warning',
};

function statusLabel(value: number) {
  return statusOptions.value.find((o) => o.value === String(value))?.label ?? value;
}

const loading = ref(false);
const list = ref<StockItem[]>([]);
const query = ref({ keyword: '', status: '' });

function loadList() {
  loading.value = true;
  // TODO: 替换为真实库存接口
  list.value = [
    { id: 1, sku: 'WSM-001', name: '示例物料 A', quantity: 120, status: 0 },
    { id: 2, sku: 'WSM-002', name: '示例物料 B', quantity: 0, status: 1 },
    { id: 3, sku: 'WSM-003', name: '示例物料 C', quantity: 15, status: 2 },
  ];
  loading.value = false;
}
</script>

<template>
  <div class="p-4">
    <el-card shadow="never">
      <template #header>
        <span class="font-medium">{{ $t('wsm.store.title') }}</span>
      </template>

      <el-form :inline="true" class="mb-3">
        <el-form-item :label="$t('wsm.store.keyword')">
          <el-input
            v-model="query.keyword"
            :placeholder="$t('wsm.store.searchPlaceholder')"
            clearable
          />
        </el-form-item>
        <el-form-item :label="$t('wsm.store.status')">
          <!-- 字典驱动的搜索下拉：options 是响应式数组，模板自动解包 -->
          <el-select
            v-model="query.status"
            :placeholder="$t('wsm.store.allStatus')"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">
            {{ $t('wsm.store.search') }}
          </el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="sku" label="SKU" min-width="140" />
        <el-table-column prop="name" :label="$t('wsm.store.name')" min-width="160" />
        <el-table-column prop="quantity" :label="$t('wsm.store.quantity')" width="100" />
        <el-table-column :label="$t('wsm.store.status')" width="90">
          <template #default="{ row }">
            <!-- 字典驱动的状态标签 -->
            <el-tag :type="statusTagType[String(row.status)] ?? 'info'">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
