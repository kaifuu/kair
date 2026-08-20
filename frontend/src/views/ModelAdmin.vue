<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">模型配置</span>
      <div class="actions">
        <el-button type="primary" @click="openCreate">新增模型</el-button>
        <el-button @click="loadAll">刷新</el-button>
      </div>
    </div>

    <div class="panel" style="padding: 12px 16px;">
      <el-tabs v-model="tab" @tab-change="loadAll">
        <!-- ========== 模型管理 ========== -->
        <el-tab-pane label="模型管理" name="models">
          <el-table :data="models" v-loading="loading" stripe>
            <el-table-column prop="name" label="名称" width="140" />
            <el-table-column label="厂商" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="providerTag[row.provider]?.t || 'info'" effect="dark">
                  {{ providerTag[row.provider]?.label || row.provider }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="baseUrl" label="接口地址" min-width="220" show-overflow-tooltip />
            <el-table-column label="模型标识" width="130">
              <template #default="{ row }"><span class="code">{{ row.modelCode }}</span></template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" :disabled="row.isDefault" @change="toggleEnabled(row)" />
              </template>
            </el-table-column>
            <el-table-column label="默认" width="70">
              <template #default="{ row }">
                <el-tag v-if="row.isDefault" size="small" type="primary" effect="dark">默认</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="testModel(row)">测试</el-button>
                <el-button link type="primary" size="small" @click="openChat(row)">对话</el-button>
                <el-button link size="small" :disabled="row.isDefault" @click="setDefault(row)">设默认</el-button>
                <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                <el-popconfirm title="确认删除该模型?" @confirm="remove(row.id)">
                  <template #reference>
                    <el-button link type="danger" size="small" :disabled="row.isDefault">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ========== Token 统计 ========== -->
        <el-tab-pane label="Token 统计" name="stats">
          <div class="tiles">
            <div class="tile">
              <div class="t-label">累计 Token</div>
              <div class="t-value">{{ fmtNum(overview.totalTokens) }}</div>
            </div>
            <div class="tile">
              <div class="t-label">今日 Token</div>
              <div class="t-value">{{ fmtNum(overview.todayTokens) }}</div>
            </div>
            <div class="tile">
              <div class="t-label">调用次数</div>
              <div class="t-value">{{ fmtNum(overview.calls) }}</div>
            </div>
            <div class="tile">
              <div class="t-label">失败次数</div>
              <div class="t-value err">{{ fmtNum(overview.failCalls) }}</div>
            </div>
            <div class="tile">
              <div class="t-label">平均耗时</div>
              <div class="t-value">{{ overview.avgDurationMs ? Math.round(overview.avgDurationMs) + ' ms' : '-' }}</div>
            </div>
          </div>

          <div class="sub-title">近 14 日 Token 消耗(prompt / completion)</div>
          <div ref="dailyRef" class="chart"></div>

          <div class="sub-title">模型用量排行(近 30 日)</div>
          <el-table :data="modelStats" v-loading="statsLoading" stripe size="small">
            <el-table-column prop="modelName" label="模型" min-width="140" />
            <el-table-column label="厂商" width="100">
              <template #default="{ row }">{{ providerTag[row.provider]?.label || row.provider }}</template>
            </el-table-column>
            <el-table-column prop="calls" label="调用次数" width="100" />
            <el-table-column prop="totalTokens" label="总 Token" width="120">
              <template #default="{ row }">{{ fmtNum(row.totalTokens) }}</template>
            </el-table-column>
            <el-table-column prop="failCalls" label="失败" width="80" />
            <el-table-column label="平均耗时" width="100">
              <template #default="{ row }">{{ row.avgDurationMs ? Math.round(row.avgDurationMs) + ' ms' : '-' }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ========== 调用记录 ========== -->
        <el-tab-pane label="调用记录" name="logs">
          <el-table :data="logs" v-loading="logLoading" stripe>
            <el-table-column prop="modelName" label="模型" width="130" />
            <el-table-column prop="scene" label="场景" width="80" />
            <el-table-column prop="promptTokens" label="Prompt" width="90" />
            <el-table-column prop="completionTokens" label="Completion" width="110" />
            <el-table-column label="总 Token" width="100">
              <template #default="{ row }"><span class="code">{{ row.totalTokens }}</span></template>
            </el-table-column>
            <el-table-column label="耗时" width="90">
              <template #default="{ row }">{{ row.durationMs != null ? row.durationMs + ' ms' : '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'SUCCESS' ? 'success' : 'danger'">
                  {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="error" label="错误" min-width="180" show-overflow-tooltip />
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination background layout="total, prev, pager, next" :total="logTotal"
                           v-model:current-page="logPage" :page-size="logSize" @current-change="loadLogs" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- ===== 模型编辑 ===== -->
    <el-dialog v-model="edit.visible" :title="edit.form.id ? '编辑模型' : '新增模型'" width="560px">
      <el-form label-width="110px">
        <el-form-item label="厂商" required>
          <el-select v-model="edit.form.provider" style="width: 100%" @change="onProviderChange">
            <el-option v-for="(v, k) in PRESETS" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" required><el-input v-model="edit.form.name" maxlength="50" /></el-form-item>
        <el-form-item label="接口地址" required>
          <el-input v-model="edit.form.baseUrl" placeholder="OpenAI 兼容基地址" />
        </el-form-item>
        <el-form-item label="模型标识" required>
          <el-input v-model="edit.form.modelCode" placeholder="如 glm-4.5 / qwen-plus / deepseek-chat" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="edit.form.apiKey" type="password" show-password
                    :placeholder="edit.form.apiKey === '******' ? '已保存,留此哨兵值则不变' : '本地模型可留空'" />
        </el-form-item>
        <el-form-item label="温度">
          <el-input-number v-model="edit.form.temperature" :min="0" :max="2" :step="0.1" />
        </el-form-item>
        <el-form-item label="最大回复">
          <el-input-number v-model="edit.form.maxTokens" :min="0" :max="32768" :step="256" />
        </el-form-item>
        <el-form-item label="超时(秒)">
          <el-input-number v-model="edit.form.timeoutSeconds" :min="5" :max="300" />
        </el-form-item>
        <el-form-item label="启用"><el-switch v-model="edit.form.enabled" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="edit.form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="edit.visible = false">取消</el-button>
        <el-button type="primary" :loading="edit.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- ===== 对话测试 ===== -->
    <el-dialog v-model="chat.visible" :title="`对话测试 - ${chat.model?.name || ''}`" width="560px">
      <div class="chat-box">
        <div v-for="(m, i) in chat.messages" :key="i" class="chat-line" :class="m.role">
          <span class="who">{{ m.role === 'user' ? '我' : '模型' }}</span>
          <span class="txt">{{ m.content }}</span>
        </div>
        <div v-if="chat.usage" class="chat-usage">
          本次消耗:prompt {{ chat.usage.promptTokens }} + completion {{ chat.usage.completionTokens }} = {{ chat.usage.totalTokens }} tokens({{ chat.usage.durationMs }} ms)
        </div>
      </div>
      <div style="display: flex; gap: 8px; margin-top: 10px;">
        <el-input v-model="chat.input" placeholder="输入消息,回车发送" @keyup.enter="sendChat" />
        <el-button type="primary" :loading="chat.sending" @click="sendChat">发送</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import http from '../api'

const PRESETS = {
  GLM: { label: '智谱 GLM', baseUrl: 'https://open.bigmodel.cn/api/paas/v4', modelCode: 'glm-4.5', key: true },
  QWEN: { label: '通义千问', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', modelCode: 'qwen-plus', key: true },
  DEEPSEEK: { label: 'DeepSeek', baseUrl: 'https://api.deepseek.com/v1', modelCode: 'deepseek-chat', key: true },
  LOCAL: { label: '本地模型(Ollama)', baseUrl: 'http://localhost:11434/v1', modelCode: 'qwen2.5:7b', key: false },
  CUSTOM: { label: '自定义(OpenAI兼容)', baseUrl: '', modelCode: '', key: true }
}
const providerTag = {
  GLM: { label: '智谱', t: 'primary' },
  QWEN: { label: '阿里', t: 'warning' },
  DEEPSEEK: { label: 'DeepSeek', t: 'danger' },
  LOCAL: { label: '本地', t: 'success' },
  CUSTOM: { label: '自定义', t: 'info' }
}

const tab = ref('models')
const loading = ref(false)
const models = ref([])
const statsLoading = ref(false)
const overview = ref({})
const modelStats = ref([])
const dailyRef = ref(null)
let chart = null

const logs = ref([])
const logLoading = ref(false)
const logPage = ref(1)
const logSize = ref(15)
const logTotal = ref(0)

const edit = reactive({
  visible: false, saving: false,
  form: { id: null, provider: 'GLM', name: '', baseUrl: '', modelCode: '', apiKey: '', temperature: 0.7, maxTokens: 0, timeoutSeconds: 60, enabled: false, remark: '' }
})
const chat = reactive({ visible: false, model: null, messages: [], input: '', sending: false, usage: null })

function fmtNum(n) {
  if (n == null) return '0'
  return Number(n).toLocaleString()
}

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(5, 19)
}

onMounted(loadAll)
onUnmounted(() => chart?.dispose())

function loadAll() {
  if (tab.value === 'models') loadModels()
  else if (tab.value === 'stats') loadStats()
  else loadLogs()
}

async function loadModels() {
  loading.value = true
  try { models.value = await http.get('/llm/models') }
  finally { loading.value = false }
}

async function loadStats() {
  statsLoading.value = true
  try {
    const [ov, daily, byModel] = await Promise.all([
      http.get('/llm/stats/overview'),
      http.get('/llm/stats/daily', { params: { days: 14 } }),
      http.get('/llm/stats/models', { params: { days: 30 } })
    ])
    overview.value = ov
    modelStats.value = byModel
    await nextTick()
    renderDaily(daily)
  } finally { statsLoading.value = false }
}

async function loadLogs() {
  logLoading.value = true
  try {
    const data = await http.get('/llm/logs', { params: { page: logPage.value, size: logSize.value } })
    logs.value = data.items
    logTotal.value = data.total
  } finally { logLoading.value = false }
}

function renderDaily(daily) {
  if (!dailyRef.value) return
  chart?.dispose()
  chart = echarts.init(dailyRef.value)
  const days = daily || []
  chart.setOption({
    grid: { left: 60, right: 20, top: 30, bottom: 30 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['Prompt', 'Completion'], top: 0, textStyle: { color: '#667085' } },
    xAxis: { type: 'category', data: days.map(d => d.date.slice(5)), axisLabel: { color: '#667085' }, axisLine: { lineStyle: { color: 'rgba(84,118,180,0.4)' } } },
    yAxis: { type: 'value', axisLabel: { color: '#667085' }, splitLine: { lineStyle: { color: '#eef2f7' } } },
    series: [
      { name: 'Prompt', type: 'bar', stack: 'tok', barMaxWidth: 26, itemStyle: { color: '#155eef', borderRadius: [0, 0, 0, 0] }, data: days.map(d => d.promptTokens) },
      { name: 'Completion', type: 'bar', stack: 'tok', barMaxWidth: 26, itemStyle: { color: '#0ea5e9', borderRadius: [4, 4, 0, 0] }, data: days.map(d => d.completionTokens) }
    ]
  })
}

function openCreate() {
  edit.form = { id: null, provider: 'GLM', name: '', baseUrl: PRESETS.GLM.baseUrl, modelCode: PRESETS.GLM.modelCode, apiKey: '', temperature: 0.7, maxTokens: 0, timeoutSeconds: 60, enabled: false, remark: '' }
  edit.visible = true
}

function openEdit(row) {
  let params = {}
  try { params = JSON.parse(row.paramsJson || '{}') } catch { /* ignore */ }
  edit.form = {
    id: row.id, provider: row.provider, name: row.name, baseUrl: row.baseUrl,
    modelCode: row.modelCode, apiKey: row.apiKey || '',
    temperature: params.temperature ?? 0.7,
    maxTokens: params.maxTokens ?? 0,
    timeoutSeconds: params.timeoutSeconds ?? 60,
    enabled: !!row.enabled, remark: row.remark || ''
  }
  edit.visible = true
}

function onProviderChange(p) {
  const pre = PRESETS[p]
  if (!edit.form.id) {
    edit.form.baseUrl = pre.baseUrl
    edit.form.modelCode = pre.modelCode
    if (!edit.form.name) edit.form.name = pre.label
  }
}

async function save() {
  const f = edit.form
  if (!f.name.trim() || !f.baseUrl.trim() || !f.modelCode.trim()) return ElMessage.warning('名称/接口地址/模型标识不能为空')
  edit.saving = true
  try {
    const body = {
      name: f.name, provider: f.provider, baseUrl: f.baseUrl, modelCode: f.modelCode,
      apiKey: f.apiKey, enabled: f.enabled, remark: f.remark,
      paramsJson: JSON.stringify({ temperature: f.temperature, maxTokens: f.maxTokens, timeoutSeconds: f.timeoutSeconds })
    }
    if (f.id) await http.put(`/llm/models/${f.id}`, body)
    else await http.post('/llm/models', body)
    ElMessage.success('已保存')
    edit.visible = false
    loadModels()
  } finally { edit.saving = false }
}

async function toggleEnabled(row) {
  try {
    await http.put(`/llm/models/${row.id}`, { enabled: row.enabled })
    ElMessage.success(row.enabled ? '已启用' : '已停用')
    loadModels()
  } catch (e) {
    loadModels()
  }
}

async function setDefault(row) {
  await http.put(`/llm/models/${row.id}/default`)
  ElMessage.success(`默认模型已切换为 ${row.name}`)
  loadModels()
}

async function remove(id) {
  await http.delete(`/llm/models/${id}`)
  ElMessage.success('已删除')
  loadModels()
}

async function testModel(row) {
  const t = ElMessage({ message: '测试调用中...', type: 'info', duration: 0 })
  try {
    const r = await http.post(`/llm/models/${row.id}/test`)
    t.close()
    ElMessage.success(`连通正常:${r.content}(${r.totalTokens} tokens / ${r.durationMs} ms)`)
  } catch (e) {
    t.close()
  }
}

function openChat(row) {
  chat.model = row
  chat.messages = []
  chat.input = ''
  chat.usage = null
  chat.visible = true
}

async function sendChat() {
  const text = chat.input.trim()
  if (!text || chat.sending) return
  chat.messages.push({ role: 'user', content: text })
  chat.input = ''
  chat.sending = true
  try {
    const r = await http.post('/llm/chat', {
      modelId: chat.model.id,
      messages: chat.messages.map(m => ({ role: m.role, content: m.content }))
    })
    chat.messages.push({ role: 'assistant', content: r.content })
    chat.usage = r
  } finally { chat.sending = false }
}
</script>

<style scoped>
.actions { display: flex; gap: 10px; }
.pager { padding: 10px 0; display: flex; justify-content: flex-end; }
.code { color: var(--primary); font-size: 12px; }
.sub-title { font-size: 14px; font-weight: 600; margin: 18px 0 10px; }
.chart { width: 100%; height: 260px; }

.tiles { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
.tile { background: rgba(21, 94, 239, 0.06); border: 1px solid rgba(21, 94, 239, 0.15); border-radius: 10px; padding: 14px 16px; }
.t-label { font-size: 12px; color: #667085; margin-bottom: 6px; }
.t-value { font-size: 22px; font-weight: 700; color: #101828; }
.t-value.err { color: #f04438; }

.chat-box { min-height: 200px; max-height: 320px; overflow-y: auto; background: rgba(84, 118, 180, 0.06); border-radius: 8px; padding: 12px; }
.chat-line { display: flex; gap: 8px; margin-bottom: 10px; }
.chat-line .who { flex: none; width: 34px; font-size: 12px; color: #667085; padding-top: 2px; }
.chat-line.assistant .who { color: #155eef; }
.chat-line .txt { white-space: pre-wrap; word-break: break-all; font-size: 13px; line-height: 1.6; }
.chat-usage { font-size: 12px; color: #667085; border-top: 1px dashed rgba(84, 118, 180, 0.3); padding-top: 8px; }
</style>
