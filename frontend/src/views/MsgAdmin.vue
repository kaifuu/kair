<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">消息管理</span>
      <div class="actions">
        <el-button type="primary" @click="openSend">发送消息</el-button>
        <el-button @click="loadAll">刷新</el-button>
      </div>
    </div>

    <div class="panel" style="padding: 12px 16px;">
      <el-tabs v-model="tab" @tab-change="loadAll">
        <!-- ========== 渠道配置 ========== -->
        <el-tab-pane label="渠道配置" name="channels">
          <el-table :data="channels" v-loading="loading" stripe>
            <el-table-column prop="name" label="通道" width="150" />
            <el-table-column label="类型" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="typeTag[row.type]?.t || 'info'" effect="dark">{{ typeTag[row.type]?.label || row.type }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" @change="saveChannel(row)" />
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="说明" min-width="220" show-overflow-tooltip />
            <el-table-column label="参数" min-width="200" show-overflow-tooltip>
              <template #default="{ row }"><span class="code">{{ configSummary(row) }}</span></template>
            </el-table-column>
            <el-table-column label="更新时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                <el-button link size="small" @click="testChannel(row)">测试</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ========== 发送记录 ========== -->
        <el-tab-pane label="发送记录" name="logs">
          <div class="toolbar">
            <el-select v-model="logChannel" clearable placeholder="全部通道" style="width: 160px" @change="logPage = 1; loadLogs()">
              <el-option v-for="(v, k) in typeTag" :key="k" :label="v.label" :value="k" />
            </el-select>
          </div>
          <el-table :data="logs" v-loading="logLoading" stripe>
            <el-table-column label="通道" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="typeTag[row.channelType]?.t || 'info'">{{ typeTag[row.channelType]?.label || row.channelType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="receivers" label="接收人" min-width="150" show-overflow-tooltip />
            <el-table-column label="结果" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'SUCCESS' ? 'success' : row.status === 'SKIP' ? 'info' : 'danger'">
                  {{ row.status === 'SUCCESS' ? '成功' : row.status === 'SKIP' ? '跳过' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="原因/耗时" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ row.error || (row.costMs != null ? row.costMs + ' ms' : '-') }}</template>
            </el-table-column>
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination background layout="total, prev, pager, next" :total="logTotal"
                           v-model:current-page="logPage" :page-size="logSize" @current-change="loadLogs" />
          </div>
        </el-tab-pane>

        <!-- ========== 站内收件箱 ========== -->
        <el-tab-pane label="站内收件箱" name="inbox">
          <div class="toolbar">
            <el-button type="primary" plain size="small" :loading="reportLoading" @click="genAiReport">
              生成 AI 态势日报(近24h)
            </el-button>
            <span class="hint">日报每日 07:36 自动生成并推送站内信,此处可按需手动生成</span>
          </div>
          <el-table :data="inbox" v-loading="inboxLoading" stripe>
            <el-table-column label="" width="60">
              <template #default="{ row }">
                <i class="dot" :class="row.read ? 'read' : 'unread'"></i>
              </template>
            </el-table-column>
            <el-table-column label="级别" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.level === 'CRITICAL' ? 'danger' : row.level === 'WARNING' ? 'warning' : 'info'">
                  {{ row.level === 'CRITICAL' ? '紧急' : row.level === 'WARNING' ? '警告' : '提示' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
            <el-table-column prop="sender" label="发送人" width="120" />
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button v-if="!row.read" link type="primary" size="small" @click="markRead(row)">已读</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination background layout="total, prev, pager, next" :total="inboxTotal"
                           v-model:current-page="inboxPage" :page-size="inboxSize" @current-change="loadInbox" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- ===== 通道编辑 ===== -->
    <el-dialog v-model="edit.visible" :title="`编辑通道 - ${edit.row?.name || ''}`" width="560px">
      <el-form label-width="130px">
        <el-form-item label="通道名称"><el-input v-model="edit.form.name" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="edit.form.enabled" /></el-form-item>
        <template v-for="f in configFields(edit.row?.type)" :key="f.key">
          <el-form-item :label="f.label">
            <el-switch v-if="f.type === 'switch'" v-model="edit.form.config[f.key]" />
            <el-input-number v-else-if="f.type === 'number'" v-model="edit.form.config[f.key]" :controls="false" style="width: 100%" />
            <el-select v-else-if="f.type === 'select'" v-model="edit.form.config[f.key]" style="width: 100%">
              <el-option v-for="o in f.options" :key="o" :label="o" :value="o" />
            </el-select>
            <el-input v-else-if="f.type === 'textarea'" v-model="edit.form.config[f.key]" type="textarea" :rows="3"
                      :placeholder="f.placeholder" />
            <el-input v-else v-model="edit.form.config[f.key]" :placeholder="f.secret && edit.form.config[f.key] === '******' ? '已保存,如需修改请输入新值' : f.placeholder || ''" />
          </el-form-item>
        </template>
        <el-form-item label="说明"><el-input v-model="edit.form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="edit.visible = false">取消</el-button>
        <el-button type="primary" :loading="edit.saving" @click="saveChannel(edit.form, true)">保存</el-button>
      </template>
    </el-dialog>

    <!-- ===== 发送消息 ===== -->
    <el-dialog v-model="send.visible" title="发送消息" width="560px">
      <el-form label-width="90px">
        <el-form-item label="发送通道" required>
          <el-checkbox-group v-model="send.channels">
            <el-checkbox v-for="c in channels.filter(c => c.enabled)" :key="c.type" :value="c.type">
              {{ c.name }}
            </el-checkbox>
          </el-checkbox-group>
          <div v-if="!channels.some(c => c.enabled)" class="hint">暂无启用通道,请先在渠道配置中启用</div>
        </el-form-item>
        <el-form-item label="级别">
          <el-radio-group v-model="send.level">
            <el-radio-button value="INFO">提示</el-radio-button>
            <el-radio-button value="WARNING">警告</el-radio-button>
            <el-radio-button value="CRITICAL">紧急</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标题" required><el-input v-model="send.title" maxlength="100" /></el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="send.content" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="接收人">
          <el-input v-model="send.receivers" placeholder="逗号分隔:邮箱/手机号/别名;留空=站内广播或通道默认" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="send.visible = false">取消</el-button>
        <el-button type="primary" :loading="send.sending" @click="doSend">发送</el-button>
      </template>
    </el-dialog>

    <!-- ===== 发送结果 ===== -->
    <el-dialog v-model="result.visible" title="发送结果" width="480px">
      <div v-for="(r, k) in result.items" :key="k" class="result-row">
        <el-tag size="small" :type="r.status === 'SUCCESS' ? 'success' : r.status === 'SKIP' ? 'info' : 'danger'" style="width: 64px; justify-content: center;">
          {{ r.status === 'SUCCESS' ? '成功' : r.status === 'SKIP' ? '跳过' : '失败' }}
        </el-tag>
        <span class="ch">{{ typeTag[k]?.label || k }}</span>
        <span class="msg">{{ r.msg }}</span>
      </div>
    </el-dialog>

    <!-- ===== AI 态势日报 ===== -->
    <el-dialog v-model="reportDlg.visible" :title="reportDlg.title" width="640px">
      <div class="ai-report">{{ reportDlg.content }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'

const typeTag = {
  JPUSH: { label: '极光推送', t: 'primary' },
  UMENG: { label: '友盟推送', t: 'warning' },
  EMAIL: { label: '邮件', t: 'success' },
  SMS: { label: '短信', t: 'danger' },
  INAPP: { label: '站内消息', t: 'info' }
}

/** 各通道可编辑参数定义(secret 字段后端掩码,回传 ****** 表示不变) */
const FORM_DEFS = {
  JPUSH: [
    { key: 'appKey', label: 'AppKey' },
    { key: 'masterSecret', label: 'MasterSecret', secret: true }
  ],
  UMENG: [
    { key: 'appKey', label: 'AppKey' },
    { key: 'appMasterSecret', label: 'AppMasterSecret', secret: true },
    { key: 'production', label: '生产模式', type: 'switch' }
  ],
  EMAIL: [
    { key: 'host', label: 'SMTP 主机', placeholder: '如 smtp.exmail.qq.com' },
    { key: 'port', label: '端口', type: 'number' },
    { key: 'username', label: '账号' },
    { key: 'password', label: '密码/授权码', secret: true },
    { key: 'from', label: '发件人', placeholder: '留空则同账号' },
    { key: 'ssl', label: 'SSL', type: 'switch' },
    { key: 'testTo', label: '测试收件箱', placeholder: '通道测试用' }
  ],
  SMS: [
    { key: 'apiUrl', label: '网关地址', placeholder: 'https://sms-gw/api/send' },
    { key: 'method', label: '请求方法', type: 'select', options: ['POST', 'GET'] },
    { key: 'bodyTemplate', label: '报文模板', type: 'textarea', placeholder: '{"phone":"${phone}","content":"${content}"}' },
    { key: 'successContains', label: '成功标识', placeholder: '响应包含该串视为成功' },
    { key: 'testPhone', label: '测试手机号' }
  ],
  INAPP: []
}

const tab = ref('channels')
const loading = ref(false)
const channels = ref([])

const logs = ref([])
const logLoading = ref(false)
const logPage = ref(1)
const logSize = ref(15)
const logTotal = ref(0)
const logChannel = ref('')

const inbox = ref([])
const inboxLoading = ref(false)
const inboxPage = ref(1)
const inboxSize = ref(15)
const inboxTotal = ref(0)

const edit = reactive({ visible: false, row: null, saving: false, form: { name: '', enabled: false, remark: '', config: {} } })
const send = reactive({ visible: false, sending: false, channels: ['INAPP'], level: 'INFO', title: '', content: '', receivers: '' })
const result = reactive({ visible: false, items: {} })

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(5, 19)
}

function configFields(type) {
  return FORM_DEFS[type] || []
}

function configSummary(row) {
  try {
    const c = JSON.parse(row.configJson || '{}')
    return Object.entries(c).filter(([, v]) => v !== '' && v != null).map(([k, v]) => `${k}=${v}`).join(' ') || '未配置'
  } catch { return '未配置' }
}

onMounted(loadAll)
function loadAll() {
  if (tab.value === 'channels') loadChannels()
  else if (tab.value === 'logs') loadLogs()
  else loadInbox()
}

async function loadChannels() {
  loading.value = true
  try { channels.value = await http.get('/msg/channels') }
  finally { loading.value = false }
}

async function loadLogs() {
  logLoading.value = true
  try {
    const data = await http.get('/msg/logs', { params: { page: logPage.value, size: logSize.value, channel: logChannel.value || undefined } })
    logs.value = data.items
    logTotal.value = data.total
  } finally { logLoading.value = false }
}

async function loadInbox() {
  inboxLoading.value = true
  try {
    const data = await http.get('/msg/inbox', { params: { page: inboxPage.value, size: inboxSize.value } })
    inbox.value = data.items
    inboxTotal.value = data.total
  } finally { inboxLoading.value = false }
}

function openEdit(row) {
  edit.row = row
  let config = {}
  try { config = JSON.parse(row.configJson || '{}') } catch { /* ignore */ }
  edit.form = { name: row.name, enabled: !!row.enabled, remark: row.remark || '', config }
  edit.visible = true
}

async function saveChannel(target, fromDialog = false) {
  const row = fromDialog ? edit.row : target
  const body = {
    name: fromDialog ? edit.form.name : target.name,
    enabled: fromDialog ? edit.form.enabled : target.enabled,
    remark: fromDialog ? edit.form.remark : undefined,
    configJson: fromDialog ? JSON.stringify(edit.form.config) : undefined
  }
  if (fromDialog) edit.saving = true
  try {
    await http.put(`/msg/channels/${row.id}`, body)
    if (fromDialog) { ElMessage.success('已保存'); edit.visible = false }
    loadChannels()
  } catch (e) {
    if (!fromDialog) loadChannels()   // switch 切换失败回滚显示
  } finally { if (fromDialog) edit.saving = false }
}

async function testChannel(row) {
  const t = ElMessage({ message: '测试发送中...', type: 'info', duration: 0 })
  try {
    const r = await http.post(`/msg/channels/${row.id}/test`)
    t.close()
    result.items = { [row.type]: r.detail || { status: r.status, msg: '' } }
    result.visible = true
    if (tab.value === 'logs') loadLogs()
  } catch (e) {
    t.close()
  }
}

function openSend() {
  send.channels = channels.value.filter(c => c.enabled).map(c => c.type).slice(0, 1)
  send.title = ''
  send.content = ''
  send.receivers = ''
  send.level = 'INFO'
  send.visible = true
}

async function doSend() {
  if (!send.channels.length) return ElMessage.warning('请选择发送通道')
  if (!send.title.trim() || !send.content.trim()) return ElMessage.warning('标题与内容不能为空')
  send.sending = true
  try {
    const r = await http.post('/msg/send', {
      channels: send.channels,
      title: send.title,
      content: send.content,
      level: send.level,
      receivers: send.receivers
    })
    send.visible = false
    result.items = r
    result.visible = true
    if (tab.value === 'logs') loadLogs()
    if (tab.value === 'inbox') loadInbox()
  } finally { send.sending = false }
}

async function markRead(row) {
  await http.post(`/msg/inbox/${row.id}/read`)
  row.read = true
}

/* AI 态势日报:近24h 数据 LLM 汇总,生成后推送站内信 */
const reportLoading = ref(false)
const reportDlg = reactive({ visible: false, title: '', content: '' })

async function genAiReport() {
  if (reportLoading.value) return
  reportLoading.value = true
  try {
    const r = await http.post('/ai/report/generate', {}, { timeout: 120000 })
    reportDlg.title = r?.title || '低空运行日报'
    reportDlg.content = r?.report || '(生成失败,请检查模型配置)'
    reportDlg.visible = true
    if (tab.value === 'inbox') loadInbox()
    if (tab.value === 'logs') loadLogs()
  } finally { reportLoading.value = false }
}
</script>

<style scoped>
.actions { display: flex; gap: 10px; }
.toolbar { display: flex; gap: 10px; margin-bottom: 10px; }
.pager { padding: 10px 0; display: flex; justify-content: flex-end; }
.code { color: var(--primary); font-size: 12px; }
.hint { color: var(--text-dim, #999); font-size: 12px; }
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; }
.dot.read { background: #475467; opacity: .4; }
.dot.unread { background: #f04438; box-shadow: 0 0 6px #f04438; }
.result-row { display: flex; align-items: center; gap: 10px; padding: 6px 0; }
.result-row .ch { width: 80px; }
.result-row .msg { color: var(--text-dim, #999); font-size: 13px; word-break: break-all; }
.ai-report { font-size: 13px; color: #344054; line-height: 1.9; white-space: pre-wrap;
  max-height: 60vh; overflow-y: auto; padding: 14px 16px;
  background: linear-gradient(180deg, #eff6ff, #f8fbff); border: 1px solid #d6e6ff; border-radius: 8px; }
</style>
