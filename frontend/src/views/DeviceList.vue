<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">设备管理</span>
      <div class="actions">
        <el-radio-group v-model="categoryFilter">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button v-for="(label, key) in CATEGORY" :key="key" :value="key">{{ label }}</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="openDialog()">新增设备</el-button>
      </div>
    </div>

    <div class="stats-row">
      <div class="stat-card"><span class="num">{{ stats.total || 0 }}</span><span class="lbl">设备总数</span></div>
      <div class="stat-card ok"><span class="num">{{ stats.online || 0 }}</span><span class="lbl">网关在线</span></div>
      <div class="stat-card fly"><span class="num">{{ stats.flying || 0 }}</span><span class="lbl">飞行中</span></div>
      <div class="stat-card warn"><span class="num">{{ stats.maintenance || 0 }}</span><span class="lbl">维保中</span></div>
      <div class="stat-card off"><span class="num">{{ stats.offline || 0 }}</span><span class="lbl">离线</span></div>
    </div>

    <div class="panel table-panel">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="编码 / 名称 / 型号" clearable style="width: 220px" @keyup.enter="load">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="statusFilter" clearable placeholder="状态" style="width: 130px">
          <el-option v-for="(label, key) in STATUS" :key="key" :label="label" :value="key" />
        </el-select>
        <el-button @click="search">查询</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe height="100%">
        <el-table-column prop="code" label="设备编码" width="130">
          <template #default="{ row }"><span class="code">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
        <el-table-column label="图标" width="60" align="center">
          <template #default="{ row }">
            <img class="row-icon" :src="resolveDeviceIcon(row, { online: row.status !== 'OFFLINE' })"
                 :class="{ custom: !!customDeviceIcon(row) }" title="地图图标" />
          </template>
        </el-table-column>
        <el-table-column label="分类" width="86">
          <template #default="{ row }">
            <el-tag size="small" :type="categoryTag[row.category]" effect="light">{{ CATEGORY[row.category] || row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="model" label="型号" width="130" show-overflow-tooltip />
        <el-table-column prop="manufacturer" label="厂商" width="100" show-overflow-tooltip />
        <el-table-column label="状态" width="88">
          <template #default="{ row }">
            <span class="status" :class="'ds-' + row.status.toLowerCase()"><i></i>{{ STATUS[row.status] || row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column label="协议" width="110">
          <template #default="{ row }">{{ row.protocol?.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="密钥" width="150">
          <template #default="{ row }">
            <span class="secret">{{ maskSecret(row.secret) }}</span>
            <el-button link type="primary" size="small" @click="copySecret(row)">复制</el-button>
            <el-button link type="warning" size="small" @click="regenSecret(row)">重置</el-button>
          </template>
        </el-table-column>
        <el-table-column label="接入" width="70">
          <template #default="{ row }">
            <el-tag size="small" :type="row.virtual ? 'info' : 'success'" effect="plain">{{ row.virtual ? '虚拟' : '真机' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近上线" width="140">
          <template #default="{ row }">{{ fmt(row.lastOnlineAt) }}</template>
        </el-table-column>
        <el-table-column prop="lastIp" label="IP" width="110">
          <template #default="{ row }">{{ row.lastIp || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="categoryFilter === 'DRONE'" label="用途" width="76">
          <template #default="{ row }">{{ row.usage || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="isCounter(categoryFilter)" label="扫描范围" width="100">
          <template #default="{ row }">{{ row.scanRange ? Math.round(row.scanRange) + ' m' : '-' }}</template>
        </el-table-column>
        <el-table-column v-if="categoryFilter === 'DRONE'" label="飞手" width="76">
          <template #default="{ row }">{{ row.pilot?.name || '-' }}</template>
        </el-table-column>
        <el-table-column v-if="categoryFilter === 'DRONE'" label="累计时长(h)" width="100">
          <template #default="{ row }">{{ row.totalFlightHours?.toFixed(1) || 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该设备?" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-row">
        <el-pagination background layout="total, sizes, prev, pager, next" :total="pager.total"
                       v-model:current-page="pager.page" v-model:page-size="pager.size"
                       :page-sizes="[10, 20, 50]" @current-change="load" @size-change="onSizeChange" />
      </div>
    </div>

    <!-- 新增/编辑 -->
    <el-dialog v-model="dialog.visible" :title="dialog.form.id ? '编辑设备' : '新增设备'" width="680px" top="6vh">
      <el-form :model="dialog.form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="设备编码" required>
              <el-input v-model="dialog.form.code" placeholder="如 UAV-2026-0007 / WS-0003" :disabled="!!dialog.form.id" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备名称" required>
              <el-input v-model="dialog.form.name" placeholder="如 7号巡检机" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="分类">
              <el-select v-model="dialog.form.category" style="width: 100%">
                <el-option v-for="(label, key) in CATEGORY" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-select v-model="dialog.form.status" style="width: 100%">
                <el-option v-for="(label, key) in STATUS" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="接入方式">
              <el-switch v-model="dialog.form.virtual" active-text="虚拟" inactive-text="真机" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="厂商">
              <el-input v-model="dialog.form.manufacturer" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="型号">
              <el-input v-model="dialog.form.model" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="地图图标">
          <div class="icon-field">
            <div class="preset-grid">
              <div v-for="p in ICON_PRESETS" :key="p.key" class="preset-item"
                   :class="{ active: dialog.form.icon === 'preset:' + p.key }"
                   :title="p.label" @click="dialog.form.icon = 'preset:' + p.key">
                <img :src="deviceSvg(p.key, { online: true })" />
                <span>{{ p.label }}</span>
              </div>
            </div>
            <div class="icon-custom">
              <img class="icon-preview" :src="resolveDeviceIcon(dialog.form, { online: true })"
                   :class="{ custom: !!customDeviceIcon(dialog.form) }" />
              <el-upload :auto-upload="false" :show-file-list="false" accept="image/*" :on-change="onIconChange">
                <el-button size="small">上传图片</el-button>
              </el-upload>
              <el-button size="small" :disabled="!dialog.form.icon" @click="dialog.form.icon = ''">恢复默认</el-button>
              <span class="icon-tip">选预设或上传 PNG/SVG(透明底 ≤200KB);留空按分类默认,无人机实时图标随航向旋转</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="TLV 协议">
          <el-select v-model="dialog.form.protocolId" clearable style="width: 100%" placeholder="Netty 接入解析用(真机必选)">
            <el-option v-for="p in protocols" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="接入密钥">
          <el-input v-model="dialog.form.secret" placeholder="留空自动生成">
            <template #append>
              <el-button @click="dialog.form.secret = genLocalSecret()">随机</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item v-if="dialog.form.category === 'CAMERA'" label="视频流地址">
          <el-input v-model="dialog.form.videoUrl"
                    placeholder="HLS(m3u8) 直播地址,经服务端代理播放;留空显示模拟画面" clearable />
        </el-form-item>

        <template v-if="dialog.form.category === 'DRONE'">
          <el-divider content-position="left">无人机参数</el-divider>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="用途">
                <el-select v-model="dialog.form.usage" clearable style="width: 100%">
                  <el-option v-for="u in USAGES" :key="u" :label="u" :value="u" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="绑定飞手">
                <el-select v-model="dialog.form.pilotId" clearable style="width: 100%">
                  <el-option v-for="p in pilots" :key="p.id" :label="p.name + ' · ' + p.org" :value="p.id" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="归航经度">
                <el-input-number v-model="dialog.form.homeLng" :precision="6" :step="0.001" :min="70" :max="140" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="归航纬度">
                <el-input-number v-model="dialog.form.homeLat" :precision="6" :step="0.001" :min="15" :max="55" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="最大航高(m)">
                <el-input-number v-model="dialog.form.maxAltitude" :min="30" :max="1000" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="续航(min)">
                <el-input-number v-model="dialog.form.maxEndurance" :min="10" :max="360" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <template v-if="isCounter(dialog.form.category)">
          <el-divider content-position="left">反制设备参数</el-divider>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="扫描范围(m)">
                <el-input-number v-model="dialog.form.scanRange" :min="100" :max="20000" :step="100"
                                 style="width: 100%" />
                <div class="form-tip">{{ isCounter(dialog.form.category) ? COUNTER_META[dialog.form.category].label +
                  ' 类型默认 ' + COUNTER_META[dialog.form.category].defaultRange + ' m(探测半径/反制作用半径)' : '' }}</div>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="装备用途">
                <el-input v-model="dialog.form.usage" placeholder="如 360° 空域搜索" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="部署经度">
                <el-input-number v-model="dialog.form.homeLng" :precision="6" :step="0.001" :min="70" :max="140" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="部署纬度">
                <el-input-number v-model="dialog.form.homeLat" :precision="6" :step="0.001" :min="15" :max="55" style="width: 100%" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import http from '../api'
import { deviceSvg, resolveDeviceIcon, customDeviceIcon, ICON_PRESETS, COUNTER_META } from '../utils/map'

const CATEGORY = {
  DRONE: '无人机', DOCK: '机库', CAMERA: '摄像头', WEATHER: '气象站', ADSB: 'ADS-B', GATEWAY: '网关', SENSOR: '传感器',
  // 无人机反制设备
  RADAR: '警戒雷达', RADIO_DETECT: '无线电探测', EO_TRACK: '光电跟踪',
  RADIO_JAM: '无线电压制', LASER: '激光处置', NET_CAPTURE: '网捕无人机'
}
const categoryTag = {
  DRONE: 'primary', DOCK: 'success', CAMERA: 'warning', WEATHER: 'info', ADSB: 'danger', GATEWAY: '', SENSOR: 'info',
  RADAR: 'warning', RADIO_DETECT: 'primary', EO_TRACK: 'success',
  RADIO_JAM: 'danger', LASER: 'danger', NET_CAPTURE: 'warning'
}
/** 反制设备分类判断与默认扫描范围 */
const isCounter = (cat) => !!COUNTER_META[cat]
const STATUS = { ONLINE: '在线', OFFLINE: '离线', IDLE: '待命', FLYING: '飞行中', MAINTENANCE: '维保' }
const USAGES = ['巡检', '航拍', '测绘', '物流', '农业', '应急', '警用']

const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')
const categoryFilter = ref('')
const rows = ref([])
const protocols = ref([])
const pilots = ref([])
const stats = ref({})
const pager = reactive({ page: 1, size: 10, total: 0 })
const dialog = reactive({ visible: false, saving: false, form: {} })

onMounted(load)
async function load() {
  loading.value = true
  try {
    ;[protocols.value, pilots.value, stats.value] = await Promise.all([
      http.get('/protocols'),
      http.get('/pilots'),
      http.get('/devices/stats')
    ])
    const res = await http.get('/devices/page', {
      params: {
        page: pager.page, size: pager.size,
        keyword: keyword.value || undefined,
        category: categoryFilter.value || undefined,
        status: statusFilter.value || undefined
      }
    })
    rows.value = res.rows || []
    pager.total = res.total || 0
  } finally { loading.value = false }
}

/** 筛选条件变化回到第一页再查;翻页保持当前页 */
watch([categoryFilter, statusFilter], () => { pager.page = 1; load() })

function search() {
  pager.page = 1
  load()
}

function onSizeChange() {
  pager.page = 1
  load()
}

function fmt(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

function maskSecret(s) {
  if (!s) return '-'
  return s.length <= 6 ? '******' : s.slice(0, 6) + '****'
}

async function copySecret(row) {
  try {
    await navigator.clipboard.writeText(row.secret || '')
    ElMessage.success('密钥已复制')
  } catch (e) {
    ElMessage.warning('复制失败,请手动记录')
  }
}

async function regenSecret(row) {
  const secret = await http.post(`/devices/${row.id}/secret`)
  row.secret = secret
  ElMessage.success(`新密钥: ${secret}`)
}

function genLocalSecret() {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789'
  let s = 'sec-'
  for (let i = 0; i < 12; i++) s += chars[Math.floor(Math.random() * chars.length)]
  return s
}

/** 上传自定义地图图标:校验类型/大小后转 dataURL 存表 */
function onIconChange(file) {
  const raw = file.raw
  if (!raw) return
  if (!raw.type.startsWith('image/')) return ElMessage.warning('仅支持图片文件(PNG/SVG/JPG)')
  if (raw.size > 200 * 1024) return ElMessage.warning('图片过大(>200KB),请压缩后上传')
  const reader = new FileReader()
  reader.onload = () => {
    dialog.form.icon = reader.result
    ElMessage.success('图标已载入,保存后生效')
  }
  reader.readAsDataURL(raw)
}

function openDialog(row) {
  dialog.form = row
    ? {
        id: row.id, code: row.code, name: row.name, category: row.category, status: row.status,
        manufacturer: row.manufacturer, model: row.model, virtual: !!row.virtual,
        protocolId: row.protocol?.id || null, secret: row.secret,
        usage: row.usage || null, pilotId: row.pilot?.id || null,
        homeLng: row.homeLng ?? 116.4, homeLat: row.homeLat ?? 39.9,
        maxAltitude: row.maxAltitude ?? 500, maxEndurance: row.maxEndurance ?? 55,
        videoUrl: row.videoUrl || '', icon: row.icon || '',
        scanRange: row.scanRange ?? (COUNTER_META[row.category]?.defaultRange ?? null)
      }
    : {
        id: null, code: '', name: '', category: 'DRONE', status: 'OFFLINE',
        manufacturer: '', model: '', virtual: false,
        protocolId: null, secret: '',
        usage: null, pilotId: null,
        homeLng: 116.4, homeLat: 39.9, maxAltitude: 500, maxEndurance: 55,
        videoUrl: '', icon: '', scanRange: null
      }
  dialog.visible = true
}

/** 切换到反制分类且未填范围时,自动带出类型默认扫描范围 */
watch(() => dialog.form.category, (cat) => {
  if (isCounter(cat) && !dialog.form.scanRange) {
    dialog.form.scanRange = COUNTER_META[cat].defaultRange
  }
})

async function save() {
  const f = dialog.form
  if (!f.code || !f.name) return ElMessage.warning('编码与名称不能为空')
  dialog.saving = true
  try {
    const body = {
      code: f.code, name: f.name, category: f.category, status: f.status,
      manufacturer: f.manufacturer, model: f.model, virtual: f.virtual,
      protocol: f.protocolId ? { id: f.protocolId } : null,
      secret: f.secret || undefined,
      usage: f.usage, homeLng: f.homeLng, homeLat: f.homeLat,
      maxAltitude: f.maxAltitude, maxEndurance: f.maxEndurance,
      pilot: f.pilotId ? { id: f.pilotId } : null,
      videoUrl: f.category === 'CAMERA' ? (f.videoUrl || '') : undefined,
      scanRange: isCounter(f.category) ? f.scanRange : undefined,
      icon: f.icon || ''
    }
    if (f.id) await http.put(`/devices/${f.id}`, body)
    else await http.post('/devices', body)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function remove(id) {
  await http.delete(`/devices/${id}`)
  ElMessage.success('已删除')
  load()
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px - 76px); padding: 8px; display: flex; flex-direction: column; }
.actions { display: flex; gap: 10px; align-items: center; }
.toolbar { display: flex; gap: 10px; padding: 8px 8px 12px; }
.toolbar + .el-table { flex: 1; }
.pager-row { display: flex; justify-content: flex-end; padding: 10px 4px 2px; }

.stats-row { display: flex; gap: 12px; margin-bottom: 10px; }
.stat-card {
  flex: 1; background: #fff; border: 1px solid var(--border); border-radius: 10px;
  padding: 10px 16px; display: flex; align-items: baseline; gap: 8px;
}
.stat-card .num { font-size: 22px; font-weight: 700; color: #101828; }
.stat-card .lbl { font-size: 12px; color: #667085; }
.stat-card.ok .num { color: #12b76a; }
.stat-card.fly .num { color: #155eef; }
.stat-card.warn .num { color: #dc6803; }
.stat-card.off .num { color: #98a2b3; }

.code { color: var(--primary); font-size: 13px; font-weight: 600; }
.secret { font-family: monospace; font-size: 12px; color: #667085; margin-right: 4px; }

.row-icon { width: 26px; height: 26px; vertical-align: middle; }
.row-icon.custom { border-radius: 5px; border: 1px solid var(--border); background: #fff; object-fit: contain; }
.icon-field { display: flex; flex-direction: column; gap: 10px; }
.preset-grid { display: flex; gap: 8px; flex-wrap: wrap; }
.preset-item {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  width: 62px; padding: 6px 2px 4px; cursor: pointer;
  border: 1.5px solid var(--border); border-radius: 9px; background: #fff;
  transition: all .15s;
}
.preset-item img { width: 32px; height: 32px; }
.preset-item span { font-size: 11px; color: var(--text-dim); }
.preset-item:hover { border-color: #b8ccf7; }
.preset-item.active { border-color: var(--primary); background: #f0f5ff; box-shadow: 0 0 0 2px rgba(21, 94, 239, .12); }
.icon-custom { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.icon-preview { width: 40px; height: 40px; }
.icon-preview.custom { border-radius: 8px; border: 1px solid var(--border); background: #fff; object-fit: contain; }
.icon-tip { font-size: 11px; color: var(--text-dim); }
.form-tip { font-size: 11px; color: var(--text-dim); line-height: 1.5; margin-top: 2px; }

.status { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; }
.status i { width: 7px; height: 7px; border-radius: 50%; }
.ds-online i { background: #12b76a; box-shadow: 0 0 8px #12b76a; }
.ds-idle i { background: #5d76a8; }
.ds-flying i { background: #155eef; box-shadow: 0 0 8px #155eef; animation: pulse-glow 1.6s infinite; }
.ds-maintenance i { background: #dc6803; }
.ds-offline i { background: #d0d5dd; }

@keyframes pulse-glow { 0%,100% { opacity: 1; } 50% { opacity: .4; } }
</style>
