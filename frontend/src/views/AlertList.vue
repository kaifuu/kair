<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">告警中心</span>
      <div class="actions">
        <el-radio-group v-model="onlyUnhandled" @change="onFilterChange">
          <el-radio-button :value="false">全部</el-radio-button>
          <el-radio-button :value="true">未处理</el-radio-button>
        </el-radio-group>
        <el-button :loading="exporting" @click="exportCsv">导出CSV</el-button>
        <el-button type="danger" plain :disabled="!selectedIds.length" @click="deleteBatch">
          批量删除{{ selectedIds.length ? `(${selectedIds.length})` : '' }}
        </el-button>
        <el-dropdown @command="clearCmd">
          <el-button type="danger">
            一键清空<i class="el-icon--right"></i>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="handled">清空已处理</el-dropdown-item>
              <el-dropdown-item command="all" divided>清空全部</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <el-table :data="rows" v-loading="loading" stripe height="100%"
                @selection-change="onSelectionChange">
        <el-table-column type="selection" width="44" />
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
        <el-table-column label="AI 研判" width="90" align="center">
          <template #default="{ row }">
            <span class="ai-flag" :class="row.aiAdvice ? 'done' : ''" @click="openAi(row)">
              <i></i>{{ row.aiAdvice ? '已研判' : '研判' }}
            </span>
          </template>
        </el-table-column>
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
            <el-popconfirm v-else title="确认删除该条告警?" @confirm="removeOne(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination background layout="total, prev, pager, next" :total="total"
                       v-model:current-page="page" :page-size="size" @current-change="load" />
      </div>
    </div>

    <!-- AI 智能研判弹窗:无研判时可一键生成 -->
    <el-dialog v-model="aiDlg.visible" width="560px" title="AI 智能研判" append-to-body>
      <template v-if="aiDlg.row">
        <div class="ai-dlg-meta">
          <el-tag size="small" effect="dark" :type="aiDlg.row.level === 'CRITICAL' ? 'danger' : aiDlg.row.level === 'WARNING' ? 'warning' : 'info'">
            {{ levelText[aiDlg.row.level] }}
          </el-tag>
          <el-tag size="small" type="primary" effect="plain">{{ typeText[aiDlg.row.type] || aiDlg.row.type }}</el-tag>
          <span class="code">{{ aiDlg.row.droneCode || '-' }}</span>
          <span class="ai-dlg-time">{{ fmtTime(aiDlg.row.createdAt) }}</span>
        </div>
        <div class="ai-dlg-msg">{{ aiDlg.row.message }}</div>
        <div class="ai-dlg-advice" v-loading="aiDlg.loading">
          <template v-if="aiDlg.advice">{{ aiDlg.advice }}</template>
          <div v-else-if="!aiDlg.loading" class="ai-dlg-empty">
            暂无研判结论
            <el-button size="small" type="primary" plain @click="genAi">生成 AI 研判</el-button>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button v-if="aiDlg.advice" size="small" :loading="aiDlg.loading" @click="genAi">重新生成</el-button>
        <el-button size="small" @click="aiDlg.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api'

const levelText = { CRITICAL: '紧急', WARNING: '警告', INFO: '提示' }
const typeText = {
  GEOFENCE_BREACH: '禁飞区闯入', ALTITUDE_EXCEED: '超限高',
  LOW_BATTERY: '低电量', SIGNAL_LOST: '失联',
  NO_LICENSE: '黑飞嫌疑', TASK_OVERDUE: '超时未归',
  PREDICTED_BREACH: '预测闯入禁飞区', CONFLICT_ALERT: '多机接近冲突',
  BATTERY_ANOMALY: '电量骤降', ALTITUDE_JUMP: '高度突变',
  SIGNAL_WEAK: '卫星信号弱'
}

const loading = ref(false)
const exporting = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(15)
const onlyUnhandled = ref(false)
const selectedIds = ref([])

/* AI 研判弹窗 */
const aiDlg = reactive({ visible: false, loading: false, row: null, advice: '' })

function openAi(row) {
  aiDlg.row = row
  aiDlg.advice = row.aiAdvice || ''
  aiDlg.visible = true
}

/** 按需研判(同步等待,LLM 生成需较长时间) */
async function genAi() {
  if (!aiDlg.row || aiDlg.loading) return
  aiDlg.loading = true
  try {
    const data = await http.post(`/ai/alert/${aiDlg.row.id}/assess`, {}, { timeout: 120000 })
    aiDlg.advice = data?.aiAdvice || aiDlg.advice
    aiDlg.row.aiAdvice = aiDlg.advice
    ElMessage.success('AI 研判完成')
  } finally { aiDlg.loading = false }
}

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

function onFilterChange() {
  page.value = 1
  load()
}

function onSelectionChange(selection) {
  selectedIds.value = selection.map(r => r.id)
}

async function handle(row) {
  await http.post(`/alerts/${row.id}/handle?handler=${encodeURIComponent(localStorage.getItem('nickname') || 'admin')}`)
  ElMessage.success('已标记处理')
  load()
}

async function exportCsv() {
  exporting.value = true
  try {
    const blob = await http.get('/alerts/export', {
      params: { unhandled: onlyUnhandled.value },
      responseType: 'blob',
      timeout: 60000
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `告警数据_${new Date().toISOString().slice(0, 10)}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } finally { exporting.value = false }
}

async function deleteBatch() {
  const n = selectedIds.value.length
  await ElMessageBox.confirm(`确认删除选中的 ${n} 条告警?删除后不可恢复。`, '批量删除', { type: 'warning' })
  const data = await http.delete('/alerts', { data: selectedIds.value })
  ElMessage.success(`已删除 ${data.deleted} 条`)
  selectedIds.value = []
  load()
}

async function removeOne(id) {
  const data = await http.delete('/alerts', { data: [id] })
  ElMessage.success(`已删除 ${data.deleted} 条`)
  load()
}

function clearCmd(cmd) {
  if (cmd === 'all') {
    ElMessageBox.confirm('确认清空全部告警?该操作不可恢复,建议先导出备份。', '清空全部', {
      type: 'error', confirmButtonText: '确认清空', confirmButtonClass: 'el-button--danger'
    }).then(() => doClear(false))
  } else {
    ElMessageBox.confirm('确认清空所有已处理的告警?', '清空已处理', { type: 'warning' })
      .then(() => doClear(true))
  }
}

async function doClear(handledOnly) {
  const data = await http.delete(`/alerts/all?handledOnly=${handledOnly}`)
  ElMessage.success(`已清空 ${data.deleted} 条`)
  page.value = 1
  load()
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px); padding: 8px; display: flex; flex-direction: column; }
.actions { display: flex; gap: 10px; align-items: center; }
.code { color: var(--primary); font-size: 13px; }
.handler { color: var(--text-dim); font-size: 12px; }

.status { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; }
.status i { width: 7px; height: 7px; border-radius: 50%; }
.status.ok i { background: #12b76a; }
.status.pending i { background: #f04438; box-shadow: 0 0 6px #f04438; animation: pulse-glow 2s infinite; }

.pager { padding: 10px; display: flex; justify-content: flex-end; }

/* AI 研判标记 */
.ai-flag { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; color: #98a2b3; cursor: pointer; user-select: none; }
.ai-flag i { width: 7px; height: 7px; border-radius: 50%; background: #d0d5dd; }
.ai-flag.done { color: #155eef; }
.ai-flag.done i { background: #155eef; }
.ai-flag:hover { color: #155eef; }

.ai-dlg-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.ai-dlg-time { font-size: 12px; color: var(--text-faint); }
.ai-dlg-msg { font-size: 13px; color: #475467; line-height: 1.6; padding: 10px 12px;
  background: #f8fafc; border-radius: 8px; margin-bottom: 12px; }
.ai-dlg-advice { min-height: 120px; font-size: 13px; color: #344054; line-height: 1.8;
  white-space: pre-wrap; padding: 14px 16px; background: linear-gradient(180deg, #eff6ff, #f8fbff);
  border: 1px solid #d6e6ff; border-radius: 8px; }
.ai-dlg-empty { text-align: center; color: var(--text-faint); padding-top: 34px; display: flex;
  flex-direction: column; align-items: center; gap: 10px; }

:deep(.el-radio-button__inner) { background: transparent; }
</style>
