<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">告警中心</span>
      <div class="actions">
        <el-radio-group v-model="onlyUnhandled">
          <el-radio-button :value="false">全部</el-radio-button>
          <el-radio-button :value="true">未处理</el-radio-button>
        </el-radio-group>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <el-table :data="rows" v-loading="loading" stripe height="100%">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="级别" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="dark" :type="row.level === 'CRITICAL' ? 'danger' : row.level === 'WARNING' ? 'warning' : 'info'">
              {{ levelText[row.level] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ typeText[row.type] || row.type }}</template>
        </el-table-column>
        <el-table-column label="无人机" width="140">
          <template #default="{ row }"><span class="code">{{ row.droneCode || '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="message" label="告警内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="位置" width="180">
          <template #default="{ row }">
            {{ row.lng ? row.lng.toFixed(4) + ', ' + row.lat.toFixed(4) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="status" :class="row.handled ? 'ok' : 'pending'">
              <i></i>{{ row.handled ? '已处理' : '未处理' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.handled" link type="primary" size="small" @click="handle(row)">处理</el-button>
            <span v-else class="handler">{{ row.handler }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total"
                       v-model:current-page="page" :page-size="size" @current-change="load" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'

const levelText = { CRITICAL: '紧急', WARNING: '警告', INFO: '提示' }
const typeText = {
  GEOFENCE_BREACH: '禁飞区闯入', ALTITUDE_EXCEED: '超限高',
  LOW_BATTERY: '低电量', SIGNAL_LOST: '失联',
  NO_LICENSE: '黑飞嫌疑', TASK_OVERDUE: '超时未归'
}

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(15)
const onlyUnhandled = ref(false)

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(5, 16)
}

onMounted(load)
async function load() {
  loading.value = true
  try {
    const data = await http.get('/alerts', {
      params: { page: page.value, size: size.value, unhandled: onlyUnhandled.value }
    })
    rows.value = data.items
    total.value = data.total
  } finally { loading.value = false }
}

async function handle(row) {
  await http.post(`/alerts/${row.id}/handle?handler=${encodeURIComponent(localStorage.getItem('nickname') || 'admin')}`)
  ElMessage.success('已标记处理')
  load()
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px); padding: 8px; display: flex; flex-direction: column; }
.actions { display: flex; gap: 10px; }
.code { color: var(--primary); font-size: 13px; }
.handler { color: var(--text-dim); font-size: 12px; }

.status { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; }
.status i { width: 7px; height: 7px; border-radius: 50%; }
.status.ok i { background: #12b76a; }
.status.pending i { background: #f04438; box-shadow: 0 0 6px #f04438; animation: pulse-glow 2s infinite; }

.pager { padding: 10px; display: flex; justify-content: flex-end; }

:deep(.el-radio-button__inner) { background: transparent; }
</style>
