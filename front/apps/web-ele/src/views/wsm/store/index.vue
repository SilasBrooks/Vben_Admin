<script lang="ts" setup>
import { ref } from 'vue';

import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

interface StockItem {
  id: number;
  sku: string;
  name: string;
  quantity: number;
  status: number;
}

const loading = ref(false);
const list = ref<StockItem[]>([]);
const query = ref({ keyword: '' });

function loadList() {
  loading.value = true;
  // TODO: 替换为真实库存接口
  list.value = [
    { id: 1, sku: 'WSM-001', name: '示例物料 A', quantity: 120, status: 0 },
    { id: 2, sku: 'WSM-002', name: '示例物料 B', quantity: 0, status: 1 },
  ];
  loading.value = false;
}
</script>

<template>
  <div class="p-4">
    <el-card shadow="never">
      <template #header>
        <span class="font-medium">库存管理</span>
      </template>

      <el-form :inline="true" class="mb-3">
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="搜索 SKU / 名称"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadList">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="sku" label="SKU" min-width="140" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="quantity" label="数量" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">
              {{ row.status === 0 ? '正常' : '缺货' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
