<template>
  <div class="monitor">
    <!-- 顶部统计条 -->
    <div class="stat-bar">
      <div class="stat-card panel" v-for="s in statCards" :key="s.label">
        <div class="stat-icon" :style="{ color: s.color, background: s.bg }">
          <el-icon :size="21"><component :is="s.icon" /></el-icon>
        </div>
        <div>
          <div class="stat-value glow-num">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <div class="monitor-body">
      <!-- 地图 -->
      <div class="map-wrap panel">
        <div ref="mapRef" class="map"></div>

        <div class="map-topbar">
          <span class="map-title">
            <el-icon color="#155eef"><Location /></el-icon>
            北京市低空监管空域 · 实时态势
          </span>
          <span class="topbar-right">
            <span class="provider-tag">{{ providerName }}</span>
            <span class="live-tag"><i></i>LIVE</span>
          </span>
        </div>

        <!-- 2D/3D 视角切换 -->
        <div class="view-toggle" title="切换地图视角">
          <span :class="{ active: !view3d }" @click="setView(false)">2D</span>
          <span :class="{ active: view3d }" @click="setView(true)">3D</span>
        </div>

        <div class="map-legend panel">
          <span><i class="lg lg-green"></i>作业区</span>
          <span><i class="lg lg-yellow"></i>限飞区</span>
          <span><i class="lg lg-red"></i>禁飞区</span>
          <span><i class="lg lg-blue"></i>实时航迹</span>
        </div>

        <div v-if="mapLoading" class="map-loading">
          <el-icon class="is-loading" :size="26" color="#155eef"><Loading /></el-icon>
          <span>地图加载中...</span>
        </div>
      </div>

      <!-- 右侧栏 -->
      <div class="side">
        <!-- 在飞列表 -->
        <div class="panel side-panel">
          <div class="panel-title">在飞无人机 <span class="title-badge">{{ flyingList.length }}</span></div>
          <div class="drone-list">
            <div v-if="!flyingList.length" class="empty">
              <el-icon :size="30" color="#c3cfe3"><VideoCameraFilled /></el-icon>
              <p>暂无在飞设备</p>
              <span class="empty-sub">到「飞行任务」下发起飞后实时监控</span>
            </div>
            <div v-for="d in flyingList" :key="d.droneId"
                 class="drone-item" :class="{ active: selectedId === d.droneId }"
                 @click="focusDrone(d)">
              <div class="drone-head">
                <span class="drone-code"><i class="fly-pulse"></i>{{ d.droneCode }}</span>
                <span class="drone-task">{{ d.taskName }}</span>
              </div>
              <div class="drone-meta">
                <span>高度 <b>{{ d.altitude }}</b>m</span>
                <span>速度 <b>{{ d.speed }}</b>m/s</span>
                <span>电量
                  <b :style="{ color: d.battery <= 20 ? '#f04438' : d.battery <= 40 ? '#f79009' : '#12b76a' }">{{ d.battery }}%</b>
                </span>
              </div>
              <el-progress :percentage="Math.round(d.battery)" :show-text="false" :stroke="5"
                           :color="d.battery <= 20 ? '#f04438' : d.battery <= 40 ? '#f79009' : '#12b76a'" />
            </div>
          </div>
        </div>

        <!-- 实时告警 -->
        <div class="panel side-panel">
          <div class="panel-title">实时告警 <span class="title-badge badge-red">{{ alerts.length }}</span></div>
          <div class="alert-list">
            <div v-if="!alerts.length" class="empty">暂无告警</div>
            <transition-group name="alert">
              <div v-for="a in alerts" :key="a.id ?? a.ts" class="alert-item" :class="levelClass(a.level)">
                <div class="alert-head">
                  <span class="alert-type">{{ typeText(a.type) }}</span>
                  <span class="alert-time">{{ fmtTime(a.createdAt) }}</span>
                </div>
                <div class="alert-msg">{{ a.message }}</div>
              </div>
            </transition-group>
          </div>
        </div>
      </div>
    </div>

    <!-- 无人机详情弹窗 -->
    <el-dialog v-model="detailVisible" width="430px" :title="detail?.droneCode + ' · 实时状态'">
      <template v-if="detail">
        <div class="detail-grid">
          <div class="d-item"><span>机型</span><b>{{ detail.model }}</b></div>
          <div class="d-item"><span>任务</span><b>{{ detail.taskName }}</b></div>
          <div class="d-item"><span>飞手</span><b>{{ detail.pilotName }}</b></div>
          <div class="d-item"><span>经度</span><b>{{ detail.lng }}</b></div>
          <div class="d-item"><span>纬度</span><b>{{ detail.lat }}</b></div>
          <div class="d-item"><span>高度</span><b>{{ detail.altitude }} m</b></div>
          <div class="d-item"><span>地速</span><b>{{ detail.speed }} m/s</b></div>
          <div class="d-item"><span>航向</span><b>{{ detail.heading }}°</b></div>
          <div class="d-item"><span>电量</span><b>{{ detail.battery }}%</b></div>
          <div class="d-item"><span>卫星</span><b>{{ detail.satellites }}</b></div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, defineEmits } from 'vue'
import http from '../api'
import { createMap, homeSvg } from '../utils/mapAdapter'
import { getProviderId, getProviderMeta } from '../utils/mapProviders'
import { Monitor, Aim, User, Bell, Location, Loading, VideoCameraFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const emit = defineEmits(['ws-status'])

/* ---------- 状态 ---------- */
const mapRef = ref(null)
const mapLoading = ref(true)
const overview = ref({})
const drones = ref([])
const flyingMap = reactive({})
const selectedId = ref(null)
const alerts = ref([])
const detailVisible = ref(false)
const detail = ref(null)
const fencesRef = ref([])

const flyingList = computed(() => Object.values(flyingMap))

const statCards = computed(() => [
  { label: '注册无人机', value: overview.value.droneTotal ?? '-', icon: Monitor, color: '#155eef', bg: '#eff6ff' },
  { label: '当前在飞', value: overview.value.flyingNow ?? '-', icon: Aim, color: '#0e9f6e', bg: '#ecfdf3' },
  { label: '持证飞手', value: overview.value.pilotTotal ?? '-', icon: User, color: '#7c3aed', bg: '#f4f3ff' },
  { label: '飞行任务', value: overview.value.taskTotal ?? '-', icon: Location, color: '#0ea5e9', bg: '#f0f9ff' },
  { label: '未处理告警', value: overview.value.alertUnhandled ?? '-', icon: Bell, color: '#f04438', bg: '#fef3f2' }
])

/* ---------- 地图对象(适配层实例,支持百度/高德/天地图) ---------- */
let mapApi = null
const overlays = {
  drones: new Map(),
  fences: []
}
const providerName = ref(getProviderMeta(getProviderId())?.name || '百度地图')

/* ---------- 初始化 ---------- */
onMounted(async () => {
  await Promise.all([loadOverview(), loadFences(), loadDrones(), loadLatestAlerts()])
  initMap()
  connectWs()
})

onUnmounted(() => {
  closeWs()
  if (mapApi) mapApi.destroy()
})

async function loadOverview() {
  try { overview.value = await http.get('/stats/overview') } catch (e) { /* noop */ }
}
async function loadFences() {
  try {
    const fences = await http.get('/fences')
    overview.value.fenceTotal = fences.length
    fencesRef.value = fences
    if (mapApi) drawFences(fences)
  } catch (e) { /* noop */ }
}
async function loadDrones() {
  try { drones.value = await http.get('/drones') } catch (e) { /* noop */ }
}
async function loadLatestAlerts() {
  try { alerts.value = (await http.get('/alerts/latest')).slice(0, 20) } catch (e) { /* noop */ }
}

/* ---------- 地图 ---------- */
/* 3D 视角:百度 tilt73/heading64.5,高德 pitch55,天地图 2D 自动忽略 */
const view3d = ref(true)

function initMap() {
  createMap(mapRef.value, { center: { lng: 116.410, lat: 39.910 }, zoom: 17, view3d: view3d.value })
    .then((api) => {
      mapApi = api
      mapLoading.value = false
      drawFences(fencesRef.value)
      drawHomePoints()
    })
    .catch((e) => {
      mapLoading.value = false
      ElMessage.error(e.message || '地图加载失败,请到「地图管理」检查密钥配置')
    })
}

/* 2D/3D 视角切换:2D 正俯视,3D 倾斜透视 */
function setView(is3d) {
  view3d.value = is3d
  mapApi?.applyView3d(is3d)
}

/** 围栏绘制 */
const FENCE_LABEL_CSS = {
  background: 'rgba(255,255,255,0.94)',
  color: '#1d2939', fontSize: '11px', fontWeight: 500, padding: '4px 8px', borderRadius: '8px',
  textAlign: 'center', lineHeight: '16px', width: '140px',
  boxShadow: '0 4px 12px -4px rgba(16,24,40,.15)',
  pointerEvents: 'none', fontFamily: 'inherit'
}

function drawFences(fences) {
  if (!mapApi || !fences) return
  overlays.fences.forEach((o) => mapApi.remove(o))
  overlays.fences = []

  const colors = { NO_FLY: '#f04438', LIMIT: '#f79009', WORK: '#12b76a' }

  fences.forEach((f) => {
    if (!f.enabled) return
    let points = []
    try { points = JSON.parse(f.pointsJson || '[]') } catch (e) { return }
    if (!points.length) return
    const color = colors[f.type] || '#155eef'

    if (f.shape === 'CIRCLE' && f.radius) {
      overlays.fences.push(mapApi.addCircle(points[0], f.radius, { color, dashed: true, fillOpacity: 0.1 }))
      addFenceLabel(points[0], f, color)
    } else if (points.length >= 3) {
      overlays.fences.push(mapApi.addPolygon(points, { color, fillOpacity: 0.1 }))
      addFenceLabel(points[Math.floor(points.length / 2)], f, color)
    }
  })
}

function addFenceLabel(pos, fence, color) {
  const typeName = { NO_FLY: '禁飞区', LIMIT: '限飞区', WORK: '作业区' }[fence.type] || '围栏'
  const html = `<b>${fence.name}</b><br/><span style="opacity:.75">${typeName}${fence.type === 'LIMIT' ? ' · 限高' + fence.maxAltitude + 'm' : ''}</span>`
  const label = mapApi.addLabel(pos, html, {
    dx: -70, dy: -18,
    css: { ...FENCE_LABEL_CSS, border: `1.5px solid ${color}` }
  })
  overlays.fences.push(label)
}

/** 停机坪(非在飞无人机归航点)— 统一 SVG 图标 */
function drawHomePoints() {
  if (!mapApi || !drones.value) return
  drones.value.forEach((d) => {
    if (!d.homeLng || !d.homeLat) return
    const colorMap = { FLYING: '#155eef', IDLE: '#667085', CHARGING: '#f79009', MAINTENANCE: '#f04438', OFFLINE: '#98a2b3' }
    const color = colorMap[d.status] || '#667085'
    const marker = mapApi.addMarker({ lng: d.homeLng, lat: d.homeLat }, {
      svg: homeSvg(color), size: 22,
      onClick: () => ElMessage.info(`${d.code} · ${d.model} · 状态:${statusText(d.status)}`)
    })
    overlays.fences.push(marker)
  })
}

function statusText(s) {
  return { IDLE: '待命', FLYING: '飞行中', CHARGING: '充电中', MAINTENANCE: '维保中', OFFLINE: '离线' }[s] || s
}

/** 更新/新增无人机图标 */
const DRONE_LABEL_CSS = {
  background: 'rgba(21,94,239,0.92)',
  color: '#fff', fontSize: '11px', fontWeight: 600, padding: '2px 8px', borderRadius: '10px',
  whiteSpace: 'nowrap', boxShadow: '0 2px 8px -2px rgba(21,94,239,.5)'
}

function updateDroneMarker(t) {
  if (!mapApi) return
  let entry = overlays.drones.get(t.droneId)

  if (!entry) {
    const marker = mapApi.addMarker({ lng: t.lng, lat: t.lat }, {
      onClick: () => {
        selectedId.value = t.droneId
        detail.value = flyingMap[t.droneId]
        detailVisible.value = true
      }
    })
    const label = mapApi.addLabel({ lng: t.lng, lat: t.lat }, t.droneCode, { dx: 16, dy: -12, css: DRONE_LABEL_CSS })
    const trackLine = mapApi.addPolyline([], { color: '#0ea5e9', weight: 3.5, opacity: 0.85 })

    entry = { marker, label, trackLine }
    overlays.drones.set(t.droneId, entry)
  }

  entry.marker.update({ lng: t.lng, lat: t.lat, heading: t.heading })
  entry.label.setPosition({ lng: t.lng, lat: t.lat })
  if (t.track && t.track.length >= 2) {
    entry.trackLine.setPath(t.track)
  }
}

/** 清理不再飞行的无人机覆盖物 */
function removeStaleMarkers() {
  const alive = new Set(flyingList.value.map((d) => d.droneId))
  for (const [id, entry] of overlays.drones) {
    if (!alive.has(id)) {
      entry.marker.destroy()
      entry.label.destroy()
      entry.trackLine.destroy()
      overlays.drones.delete(id)
    }
  }
}

function focusDrone(t) {
  selectedId.value = t.droneId
  mapApi?.flyTo(t, 14)
}

/* ---------- WebSocket ---------- */
let ws = null
let reconnectTimer = null

function connectWs() {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${proto}://${location.host}/ws/telemetry`)

  ws.onopen = () => {
    emit('ws-status', true)
    if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
  }
  ws.onclose = () => {
    emit('ws-status', false)
    reconnectTimer = setTimeout(connectWs, 5000)
  }
  ws.onerror = () => ws.close()

  ws.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data)
      if (msg.type === 'telemetry' && Array.isArray(msg.payload)) {
        let hasChange = false
        msg.payload.forEach((t) => {
          flyingMap[t.droneId] = t
          updateDroneMarker(t)
          hasChange = true
        })
        if (hasChange) {
          removeStaleMarkers()
          loadOverview()
        }
      } else if (msg.type === 'alert') {
        alerts.value.unshift(msg.payload)
        if (alerts.value.length > 20) alerts.value.pop()
        loadOverview()
        if (msg.payload.lng && mapApi) {
          flashAlertPoint(msg.payload)
        }
      }
    } catch (e) { /* ignore */ }
  }
}

function closeWs() {
  if (reconnectTimer) clearTimeout(reconnectTimer)
  if (ws) { ws.onclose = null; ws.close() }
}

/** 告警位置涟漪 */
let flashTimers = []
function flashAlertPoint(a) {
  const color = a.level === 'CRITICAL' ? '#f04438' : '#f79009'
  const circle = mapApi.addCircle(a, 200, { color, weight: 2.5, fillOpacity: 0.28 })
  const timer = setTimeout(() => {
    mapApi.remove(circle)
    flashTimers = flashTimers.filter((t) => t !== timer)
  }, 8000)
  flashTimers.push(timer)
}

/* ---------- 工具 ---------- */
function levelClass(level) {
  return { CRITICAL: 'lv-critical', WARNING: 'lv-warning', INFO: 'lv-info' }[level] || 'lv-info'
}
function typeText(type) {
  return {
    GEOFENCE_BREACH: '禁飞区闯入', ALTITUDE_EXCEED: '超限高',
    LOW_BATTERY: '低电量', SIGNAL_LOST: '失联',
    NO_LICENSE: '黑飞嫌疑', TASK_OVERDUE: '超时未归'
  }[type] || '告警'
}
function fmtTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(5, 16)
}
</script>

<style scoped>
.monitor {
  height: 100%;
  display: flex; flex-direction: column;
  padding: 16px;
  gap: 14px;
  overflow: hidden;
}

/* 统计条 */
.stat-bar { display: flex; gap: 14px; }
.stat-card {
  flex: 1;
  display: flex; align-items: center; gap: 13px;
  padding: 14px 18px;
  transition: all .25s;
}
.stat-card:hover { box-shadow: var(--shadow-md); transform: translateY(-2px); }
.stat-icon {
  width: 44px; height: 44px; border-radius: 11px;
  display: flex; align-items: center; justify-content: center;
}
.stat-value { font-size: 26px; line-height: 1.1; }
.stat-label { color: var(--text-dim); font-size: 12.5px; margin-top: 3px; }

/* 主体 */
.monitor-body { flex: 1; display: flex; gap: 14px; min-height: 0; }

.map-wrap { flex: 1; overflow: hidden; position: relative; }
.map { width: 100%; height: 100%; }

.map-topbar {
  position: absolute; top: 12px; left: 12px; right: 12px;
  display: flex; justify-content: space-between; align-items: center;
  pointer-events: none; z-index: 5;
}
.map-title {
  display: inline-flex; align-items: center; gap: 7px;
  padding: 7px 14px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-radius: 9px;
  font-size: 13px; font-weight: 600; color: #1d2939;
  box-shadow: var(--shadow-sm);
}
.live-tag {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-radius: 9px;
  font-size: 12px; font-weight: 700; color: #f04438; letter-spacing: 2px;
  box-shadow: var(--shadow-sm);
}
.live-tag i { width: 7px; height: 7px; border-radius: 50%; background: #f04438; animation: pulse-dot 1.4s infinite; }

.topbar-right { display: flex; align-items: center; gap: 8px; }
.provider-tag {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-radius: 9px;
  font-size: 12px; font-weight: 600; color: #155eef;
  box-shadow: var(--shadow-sm);
}
.provider-tag::before {
  content: ''; width: 7px; height: 7px; border-radius: 50%;
  background: #155eef; opacity: .8;
}

/* 2D/3D 视角切换 */
.view-toggle {
  position: absolute; top: 54px; right: 12px; z-index: 5;
  display: inline-flex; align-items: center; gap: 2px;
  padding: 3px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-radius: 9px;
  box-shadow: var(--shadow-sm);
  font-size: 12px; font-weight: 700;
}
.view-toggle span {
  padding: 4px 12px; border-radius: 7px; cursor: pointer;
  color: var(--text-dim); transition: all .2s; user-select: none;
}
.view-toggle span:hover { color: #155eef; }
.view-toggle span.active {
  background: #155eef; color: #fff;
  box-shadow: 0 2px 6px -2px rgba(21, 94, 239, .5);
}

.map-legend {
  position: absolute; left: 12px; bottom: 12px;
  display: flex; gap: 14px;
  padding: 8px 14px;
  font-size: 12px; color: #475467;
  border-radius: 9px;
  z-index: 5;
}
.lg { display: inline-block; width: 10px; height: 10px; border-radius: 3px; margin-right: 5px; vertical-align: -1px; }
.lg-green { background: rgba(18, 183, 106, 0.75); }
.lg-yellow { background: rgba(247, 144, 9, 0.75); }
.lg-red { background: rgba(240, 68, 56, 0.75); }
.lg-blue { background: #0ea5e9; height: 3px; border-radius: 1px; vertical-align: 3px; }

.map-loading {
  position: absolute; inset: 0;
  display: flex; flex-direction: column; gap: 10px;
  align-items: center; justify-content: center;
  color: #155eef;
  background: rgba(243, 245, 249, 0.8);
  z-index: 10; font-size: 13px;
}

/* 侧栏 */
.side { width: 340px; display: flex; flex-direction: column; gap: 14px; min-height: 0; }
.side-panel { flex: 1; display: flex; flex-direction: column; min-height: 0; overflow: hidden; }

.title-badge {
  font-size: 11px; padding: 1px 8px; border-radius: 999px; font-weight: 700;
  background: #eff6ff; color: #155eef;
  border: 1px solid #d6e4ff;
}
.badge-red { background: #fef3f2; color: #f04438; border-color: #fee4e2; }

.drone-list, .alert-list { flex: 1; overflow-y: auto; padding: 4px 12px 12px; }

.drone-item {
  padding: 11px 13px; margin-bottom: 9px;
  border: 1px solid var(--border);
  border-radius: 11px;
  background: #fff;
  cursor: pointer;
  transition: all .2s;
}
.drone-item:hover { border-color: #b8ccf7; box-shadow: var(--shadow-sm); }
.drone-item.active { border-color: #155eef; background: #f5f8ff; box-shadow: inset 0 0 0 1px #d6e4ff; }

.drone-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 7px; }
.drone-code {
  font-weight: 700; color: #155eef; font-size: 13px;
  display: inline-flex; align-items: center; gap: 7px;
}
.fly-pulse {
  width: 8px; height: 8px; border-radius: 50%;
  background: #0ea5e9;
  animation: fly-ping 1.8s infinite;
}
@keyframes fly-ping {
  0% { box-shadow: 0 0 0 0 rgba(14, 165, 233, .5); }
  70% { box-shadow: 0 0 0 7px rgba(14, 165, 233, 0); }
  100% { box-shadow: 0 0 0 0 rgba(14, 165, 233, 0); }
}
.drone-task { font-size: 11.5px; color: var(--text-dim); max-width: 170px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.drone-meta { display: flex; gap: 11px; font-size: 12px; color: var(--text-dim); margin-bottom: 7px; }
.drone-meta b { color: #101828; font-weight: 600; }

/* 告警 */
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

.empty {
  padding: 34px 0; text-align: center; color: var(--text-faint);
  font-size: 13px; line-height: 1.8; display: flex; flex-direction: column; align-items: center; gap: 6px;
}
.empty p { margin: 0; }
.empty-sub { font-size: 11.5px; opacity: 0.8; }

/* 详情 */
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px 14px; }
.d-item {
  display: flex; justify-content: space-between;
  padding: 9px 13px;
  background: #f8fafc;
  border: 1px solid var(--border);
  border-radius: 9px; font-size: 13px;
}
.d-item span { color: var(--text-dim); }
</style>
