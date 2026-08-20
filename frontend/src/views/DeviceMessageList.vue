<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">报文管理</span>
      <span class="page-sub">Netty 网关上下行整帧留痕 · 指令下发</span>
      <div class="actions">
        <el-switch v-model="autoRefresh" active-text="5s 自动刷新" @change="toggleAuto" />
        <el-button :icon="Refresh" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Promotion" @click="openSend">发送指令</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-select v-model="deviceId" filterable clearable placeholder="全部设备" style="width: 200px" @change="search">
          <el-option v-for="d in devices" :key="d.id" :value="d.id" :label="`${d.code} · ${d.name}`" />
        </el-select>
        <el-radio-group v-model="direction" @change="search">
          <el-radio-button value="">全部方向</el-radio-button>
          <el-radio-button value="UP">上行(收)</el-radio-button>
          <el-radio-button value="DOWN">下行(发)</el-radio-button>
        </el-radio-group>
        <el-select v-model="frameType" clearable placeholder="全部帧类型" style="width: 150px" @change="search">
          <el-option v-for="t in FRAME_TYPES" :key="t" :label="t" :value="t" />
        </el-select>
        <el-select v-model="displayBase" style="width: 130px">
          <el-option v-for="b in BASES" :key="b.value" :label="b.label + '展示'" :value="b.value" />
        </el-select>
      </div>

      <el-table :data="items" v-loading="loading" stripe height="100%">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-box">
              <div v-for="b in BASES" :key="b.value" class="expand-line">
                <span class="el-label">{{ b.label }}</span>
                <code>{{ contentIn(row.contentHex, b.value) }}</code>
              </div>
              <el-button size="small" text type="primary" @click="copy(row.contentHex)">复制 HEX</el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="方向" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.direction === 'UP' ? 'primary' : 'success'" effect="light">
              {{ row.direction === 'UP' ? '↑ 上行' : '↓ 下行' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="帧类型" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.frameType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deviceCode" label="设备编码" width="140">
          <template #default="{ row }">{{ row.deviceCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="length" label="字节" width="70" />
        <el-table-column label="报文内容" min-width="360">
          <template #default="{ row }">
            <code class="content">{{ contentIn(row.contentHex, displayBase) }}</code>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total"
                       layout="total, prev, pager, next, jumper" background @current-change="load" />
      </div>
    </div>

    <!-- 手动下发指令 -->
    <el-dialog v-model="send.visible" title="发送指令(COMMAND 帧)" width="640px">
      <el-form label-width="90px">
        <el-form-item label="目标设备" required>
          <el-select v-model="send.deviceId" filterable placeholder="选择设备(在线可下发)" style="width: 100%">
            <el-option v-for="d in sendDevices" :key="d.id" :value="d.id"
                       :label="`${d.code} · ${d.name}(${statusText[d.status] || d.status})`"
                       :disabled="d.virtual" />
          </el-select>
          <div class="field-tip">经 Netty TCP 网关下发:自动组帧(magic AA55 + CRC16)并计入下方报文日志</div>
        </el-form-item>
        <el-form-item label="输入进制">
          <el-select v-model="send.base" style="width: 160px">
            <el-option v-for="b in BASES" :key="b.value" :label="b.label" :value="b.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="报文内容">
          <el-input v-model="send.content" type="textarea" :rows="3"
                    :placeholder="placeholder" style="font-family: monospace" />
        </el-form-item>
        <el-form-item label="预览">
          <div class="preview">
            <div v-if="preview.error" class="preview-err">{{ preview.error }}</div>
            <template v-else>
              <div v-for="b in BASES" :key="b.value" class="expand-line">
                <span class="el-label">{{ b.label }}</span>
                <code>{{ preview.text[b.value] || '(空)' }}</code>
              </div>
            </template>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="send.visible = false">关闭</el-button>
        <el-button type="primary" :loading="send.saving" :disabled="!!preview.error" @click="doSend">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion, Refresh } from '@element-plus/icons-vue'
import http from '../api'
import { BASES, hexToBytes, encodeBytes, decodeBytes, bytesToHex } from '../utils/codec'

const FRAME_TYPES = ['REGISTER', 'HEARTBEAT', 'DATA', 'ACK', 'COMMAND']
const statusText = { ONLINE: '在线', OFFLINE: '离线', IDLE: '空闲', FLYING: '飞行中', MAINTENANCE: '维护' }
const PLACEHOLDER = {
  hex: 'AA 55 01 02(两位一组,可含 0x 前缀)',
  dec: '170 85 1 2(每字节 0..255)',
  oct: '252 125 1 2(每字节 0..377)',
  bin: '10101010 01010101(每字节 8 位)'
}

const loading = ref(false)
const items = ref([])
const devices = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const deviceId = ref(null)
const direction = ref('')
const frameType = ref('')
const displayBase = ref('hex')
const autoRefresh = ref(false)
let timer = null

const send = reactive({ visible: false, saving: false, deviceId: null, base: 'hex', content: '' })

onMounted(async () => {
  await Promise.all([load(), loadDevices()])
})

async function load() {
  loading.value = true
  try {
    const data = await http.get('/device-messages', {
      params: {
        deviceId: deviceId.value || undefined,
        direction: direction.value || undefined,
        frameType: frameType.value || undefined,
        page: page.value - 1, size
      }
    })
    items.value = data.items
    total.value = data.total
  } finally { loading.value = false }
}

async function loadDevices() {
  devices.value = await http.get('/devices')
}

function search() {
  page.value = 1
  load()
}

function fmt(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

/** 整帧 HEX → 指定进制文本(展示用,缓存避免每行重复解析) */
const contentCache = new Map()
function contentIn(hex, base) {
  if (!hex) return ''
  const key = base + '|' + hex
  if (!contentCache.has(key)) {
    try {
      contentCache.set(key, encodeBytes(hexToBytes(hex), base))
    } catch (e) {
      contentCache.set(key, hex)
    }
    if (contentCache.size > 2000) contentCache.clear()   // 防长会话膨胀
  }
  return contentCache.get(key)
}

function copy(text) {
  navigator.clipboard?.writeText(text).then(
    () => ElMessage.success('已复制'),
    () => ElMessage.warning('复制失败,请手动选择')
  )
}

function toggleAuto(on) {
  clearInterval(timer)
  if (on) timer = setInterval(load, 5000)
}

onBeforeUnmount(() => clearInterval(timer))

/* ---------- 手动下发 ---------- */

const sendDevices = computed(() =>
  [...devices.value].sort((a, b) => Number(b.status === 'ONLINE') - Number(a.status === 'ONLINE'))
)

const placeholder = computed(() => PLACEHOLDER[send.base] || '')

const preview = computed(() => {
  try {
    const bytes = decodeBytes(send.content, send.base)
    if (!bytes.length) return { error: '请输入报文内容' }
    const text = {}
    for (const b of BASES) text[b.value] = encodeBytes(bytes, b.value)
    return { error: '', text, bytes }
  } catch (e) {
    return { error: e.message }
  }
})

function openSend() {
  send.deviceId = deviceId.value
  send.base = 'hex'
  send.content = ''
  send.visible = true
}

async function doSend() {
  if (!send.deviceId) return ElMessage.warning('请选择目标设备')
  const { bytes, error } = preview.value
  if (error || !bytes?.length) return
  send.saving = true
  try {
    const res = await http.post(`/devices/${send.deviceId}/messages`, {
      base: 'hex',
      content: bytesToHex(bytes)
    })
    ElMessage.success(`已下发 ${res.length}B 整帧`)
    send.visible = false
    page.value = 1
    load()
  } finally { send.sending = false }
}
</script>

<style scoped>
.page-sub { font-size: 13px; color: var(--text-dim); }
.table-panel { height: calc(100% - 50px); padding: 8px; display: flex; flex-direction: column; }
.actions { display: flex; gap: 12px; align-items: center; }
.toolbar { display: flex; gap: 10px; padding: 8px 8px 12px; align-items: center; }
.toolbar + .el-table { flex: 1; }
.pager { display: flex; justify-content: flex-end; padding: 12px 8px 4px; }

code.content, .expand-line code {
  font-family: Consolas, monospace; font-size: 12px; color: #344054;
  word-break: break-all; white-space: normal;
}
.expand-box { padding: 6px 20px; }
.expand-line { display: flex; gap: 12px; margin-bottom: 6px; align-items: baseline; }
.expand-line .el-label, .preview .el-label { width: 56px; flex-shrink: 0; font-size: 12px; color: #98a2b3; }
.field-tip { width: 100%; margin-top: 6px; font-size: 12px; color: var(--text-dim); line-height: 1.7; }

.preview { width: 100%; border: 1px solid var(--border); border-radius: 8px; padding: 10px 12px; }
.preview-err { font-size: 12.5px; color: #f04438; }
</style>
