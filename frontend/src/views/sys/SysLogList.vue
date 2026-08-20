<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">日志管理</span>
      <div class="actions">
        <el-radio-group v-model="typeFilter">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="OPERATE">操作日志</el-radio-button>
          <el-radio-button value="LOGIN">登录日志</el-radio-button>
          <el-radio-button value="DEVICE">设备日志</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="账号 / 动作 / 详情" clearable style="width: 240px" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="items" v-loading="loading" stripe height="100%">
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag[row.type]" effect="light">{{ typeText[row.type] || row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="账号/设备" width="140">
          <template #default="{ row }">{{ row.username || '-' }}</template>
        </el-table-column>
        <el-table-column prop="action" label="动作" width="160" show-overflow-tooltip />
        <el-table-column prop="detail" label="详情" min-width="260" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="120">
          <template #default="{ row }">{{ row.ip || '-' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.success ? 'success' : 'danger'" effect="plain">{{ row.success ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total"
                       layout="total, prev, pager, next, jumper" background @current-change="load" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import http from '../../api'
import { Search } from '@element-plus/icons-vue'

const typeText = { OPERATE: '操作', LOGIN: '登录', DEVICE: '设备' }
const typeTag = { OPERATE: 'primary', LOGIN: 'success', DEVICE: 'warning' }

const loading = ref(false)
const typeFilter = ref('')
const keyword = ref('')
const items = ref([])
const total = ref(0)
const page = ref(1)
const size = 20

onMounted(load)
async function load() {
  loading.value = true
  try {
    const data = await http.get('/logs', {
      params: {
        type: typeFilter.value || undefined,
        keyword: keyword.value || undefined,
        page: page.value - 1, size
      }
    })
    items.value = data.items
    total.value = data.total
  } finally { loading.value = false }
}

function search() {
  page.value = 1
  load()
}

function fmt(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px); padding: 8px; display: flex; flex-direction: column; }
.actions { display: flex; gap: 10px; align-items: center; }
.toolbar { display: flex; gap: 10px; padding: 8px 8px 12px; }
.toolbar + .el-table { flex: 1; }
.pager { display: flex; justify-content: flex-end; padding: 12px 8px 4px; }
</style>
