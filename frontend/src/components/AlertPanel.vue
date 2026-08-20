<template>
  <div class="ap">
    <div class="ap-filter">
      <el-radio-group v-model="unhandledOnly" size="small" @change="onFilterChange">
        <el-radio-button :value="false">全部</el-radio-button>
        <el-radio-button :value="true">未处理</el-radio-button>
      </el-radio-group>
    </div>

    <div class="ap-list" v-loading="loading">
      <div v-if="!rows.length && !loading" class="ap-empty">暂无告警</div>
      <transition-group name="alert">
        <div v-for="a in rows" :key="a.id" class="alert-item" :class="levelClass(a.level)">
          <div class="alert-head">
            <span class="alert-type">{{ typeText(a.type) }}</span>
            <span class="alert-time">{{ fmtTime(a.createdAt) }}</span>
          </div>
          <div class="alert-msg">{{ a.message }}</div>
        </div>
      </transition-group>
    </div>

    <div class="ap-pager">
      <el-pagination small background layout="total, prev, pager, next"
                     :total="total" :page-size="size" :current-page="page"
                     pager-count="5"
                     @current-change="onPage" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import http from '../api'

const size = 8
const page = ref(1)
const total = ref(0)
const rows = ref([])
const loading = ref(false)
const unhandledOnly = ref(false)

/** WS 新告警:去抖 800ms,仅在首页时刷新列表(避免打断翻页阅读),总数即时+1 */
let pending = null
let newCount = 0

onMounted(load)
onUnmounted(() => { if (pending) clearTimeout(pending) })

async function load() {
  loading.value = true
  try {
    const res = await http.get('/alerts', {
      params: { page: page.value, size, unhandled: unhandledOnly.value ? true : undefined }
    })
    rows.value = res.items || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function onPage(p) {
  page.value = p
  load()
}

function onFilterChange() {
  page.value = 1
  load()
}

function onNewAlert() {
  newCount++
  total.value++
  if (pending) clearTimeout(pending)
  pending = setTimeout(() => {
    pending = null
    if (newCount > 0 && page.value === 1) load()
    newCount = 0
  }, 800)
}

defineExpose({ onNewAlert, reload: load })

function levelClass(level) {
  return { CRITICAL: 'lv-critical', WARNING: 'lv-warning', INFO: 'lv-info' }[level] || 'lv-info'
}
function typeText(type) {
  return {
    GEOFENCE_BREACH: '禁飞区闯入', ALTITUDE_EXCEED: '超限高',
    LOW_BATTERY: '低电量', SIGNAL_LOST: '失联',
    NO_LICENSE: '黑飞嫌疑', TASK_OVERDUE: '超时未归',
    PREDICTED_BREACH: '预测闯入', CONFLICT_ALERT: '多机冲突',
    BATTERY_ANOMALY: '电量骤降', ALTITUDE_JUMP: '高度突变',
    SIGNAL_WEAK: '信号弱'
  }[type] || '告警'
}
function fmtTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(5, 16)
}
</script>

<style scoped>
.ap { display: flex; flex-direction: column; min-height: 0; flex: 1; }
.ap-filter { padding: 2px 12px 8px; display: flex; }
.ap-list { flex: 1; overflow-y: auto; padding: 0 12px 8px; min-height: 80px; max-height: 250px; }
.ap-empty { padding: 26px 0; text-align: center; color: var(--text-faint); font-size: 13px; }

.ap-pager {
  flex-shrink: 0; display: flex; justify-content: flex-end;
  padding: 8px 12px 10px; border-top: 1px solid var(--border);
}
.ap-pager :deep(.el-pagination) { --el-pagination-button-height: 22px; }

.alert-item {
  padding: 9px 12px; margin-bottom: 9px;
  border-radius: 10px; border: 1px solid;
}
.lv-critical { border-color: #fee4e2; background: #fff5f5; }
.lv-warning { border-color: #fef0c7; background: #fffcf5; }
.lv-info { border-color: var(--border); background: #fff; }

.alert-head { display: flex; justify-content: space-between; margin-bottom: 3px; }
.alert-type { font-size: 12px; font-weight: 700; }
.lv-critical .alert-type { color: #d92d20; }
.lv-warning .alert-type { color: #dc6803; }
.lv-info .alert-type { color: #155eef; }
.alert-time { font-size: 11px; color: var(--text-faint); }
.alert-msg { font-size: 12px; color: #475467; line-height: 1.55; }

.alert-enter-active { transition: all 0.4s ease; }
.alert-enter-from { opacity: 0; transform: translateX(16px); }
</style>
