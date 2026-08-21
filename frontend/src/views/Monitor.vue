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
      <div class="map-wrap panel" :class="{ fs: mapFullscreen }">
        <div ref="mapRef" class="map"></div>

        <div class="map-topbar">
          <span class="map-title">
            <el-icon color="#155eef"><Location /></el-icon>
            北京市低空监管空域 · 实时态势
          </span>
          <span class="topbar-right">
            <span class="topbar-btn" :title="mapFullscreen ? '退出全屏 (Esc)' : '地图全屏'"
                  @click="toggleFullscreen">
              <el-icon :size="15"><FullScreen /></el-icon>
            </span>
            <span class="topbar-btn" :title="sideCollapsed ? '展开右侧面板' : '收起右侧面板'"
                  @click="toggleSide">
              <el-icon :size="15"><component :is="sideCollapsed ? Expand : Fold" /></el-icon>
            </span>
            <span class="provider-tag">{{ providerName }}</span>
            <span class="live-tag"><i></i>LIVE</span>
          </span>
        </div>

        <!-- 2D/3D 视角切换 -->
        <div class="view-toggle" title="切换地图视角">
          <span :class="{ active: !view3d }" @click="setView(false)">2D</span>
          <span :class="{ active: view3d }" @click="setView(true)">3D</span>
        </div>

        <!-- 地图工具箱:比例尺/工具条/方向盘/鹰眼/3D罗盘/测距/面积测算 -->
        <MapToolbox :map="toolboxMap" :fences="fencesRef" :devices="devices"
                    :flying="flyingList" @scale="(v) => (legendLift = v)" />

        <div class="map-legend panel" :class="{ lift: legendLift }">
          <span><i class="lg lg-green"></i>作业区</span>
          <span><i class="lg lg-yellow"></i>限飞区</span>
          <span><i class="lg lg-red"></i>禁飞区</span>
          <span><i class="lg lg-blue"></i>实时航迹</span>
        </div>

        <div v-if="mapLoading" class="map-loading">
          <el-icon class="is-loading" :size="26" color="#155eef"><Loading /></el-icon>
          <span>地图加载中...</span>
        </div>

        <!-- 轨迹回放控制条(在线/离线无人机均可回放) -->
        <div v-if="replay.active" class="replay-bar panel">
          <span class="rp-title">
            <el-icon color="#7c3aed"><VideoPlay /></el-icon>
            {{ replay.deviceCode }} · 轨迹回放
          </span>
          <el-button circle size="small" :disabled="replay.idx >= replay.points.length - 1 && !replay.playing"
                     @click="toggleReplayPlay">
            <el-icon><VideoPause v-if="replay.playing" /><VideoPlay v-else /></el-icon>
          </el-button>
          <el-slider v-model="replay.idx" class="rp-slider" :min="0"
                     :max="Math.max(1, replay.points.length - 1)" :show-tooltip="false"
                     @change="onReplaySeek" />
          <span class="rp-time">{{ replayTime }}</span>
          <el-select v-model="replay.speed" size="small" class="rp-speed" @change="onReplaySpeed">
            <el-option v-for="s in [1, 2, 4, 8, 16]" :key="s" :label="s + 'x'" :value="s" />
          </el-select>
          <span class="rp-count">{{ replay.idx + 1 }}/{{ replay.points.length }}</span>
          <el-button link type="danger" size="small" @click="stopReplay">退出回放</el-button>
        </div>
      </div>

      <!-- 右侧栏:四块可收缩面板(整栏可统一收起,收起后地图占满) -->
      <div class="side" v-show="!sideCollapsed">
        <CollapsiblePanel v-model:collapsed="collapse.drones" title="无人机">
          <template #badge>
            <span class="title-badge">在飞 {{ flyingList.length }}</span>
          </template>
          <DronePanel :drones="drones" :flying="flyingList" :selected-id="selectedId"
                      @focus="focusDrone" @open="openDevice" @replay="startReplay" />
        </CollapsiblePanel>

        <CollapsiblePanel v-model:collapsed="collapse.sensors" title="物联网传感">
          <template #badge>
            <span class="title-badge">{{ sensorDevices.length }}</span>
          </template>
          <SensorPanel :devices="sensorDevices" :latest="latestData" @open="openDevice" />
        </CollapsiblePanel>

        <CollapsiblePanel v-model:collapsed="collapse.video" title="视频监控">
          <template #badge>
            <span class="title-badge">{{ cameraDevices.length }}</span>
          </template>
          <VideoPanel :devices="cameraDevices" :latest="latestData" @open="openDevice" />
        </CollapsiblePanel>
      </div>
    </div>

    <!-- AI 值班助手悬浮球(拖动移位,弹出窗口可拖动/调整大小,流式回复) -->
    <AiCopilotBall />

    <!-- 在飞无人机实时状态弹窗(实时视频回传 + 遥测随 WS 刷新) -->
    <el-dialog v-model="detailVisible" width="430px" :title="liveDetail?.droneCode + ' · 实时状态'">
      <template v-if="liveDetail">
        <DroneVideo class="detail-video" :telemetry="liveDetail" :device="detailDevice" />
        <div class="detail-grid">
          <div class="d-item"><span>机型</span><b>{{ liveDetail.model }}</b></div>
          <div class="d-item"><span>任务</span><b>{{ liveDetail.taskName }}</b></div>
          <div class="d-item"><span>飞手</span><b>{{ liveDetail.pilotName }}</b></div>
          <div class="d-item"><span>经度</span><b>{{ liveDetail.lng }}</b></div>
          <div class="d-item"><span>纬度</span><b>{{ liveDetail.lat }}</b></div>
          <div class="d-item"><span>高度</span><b>{{ liveDetail.altitude }} m</b></div>
          <div class="d-item"><span>地速</span><b>{{ liveDetail.speed }} m/s</b></div>
          <div class="d-item"><span>航向</span><b>{{ liveDetail.heading }}°</b></div>
          <div class="d-item"><span>电量</span><b>{{ liveDetail.battery }}%</b></div>
          <div class="d-item"><span>卫星</span><b>{{ liveDetail.satellites }}</b></div>
        </div>
      </template>
    </el-dialog>

    <!-- 设备详情(地图 ICON / 面板行点击):基础信息 + 历史数据 -->
    <DeviceInfoDialog v-model="deviceDlg.visible" :device="deviceDlg.device" :latest="latestData" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, defineEmits } from 'vue'
import http from '../api'
import { createMap } from '../utils/mapAdapter'
import { deviceSvg, resolveDeviceIcon, customDeviceIcon, parseFenceShapes, COUNTER_META, deviceMeta } from '../utils/map'
import { pushAlert } from '../stores/alertCenter'
import { getProviderId, getProviderMeta } from '../utils/mapProviders'
import { Monitor, Aim, User, Bell, Location, Loading, FullScreen, Fold, Expand, VideoPlay, VideoPause } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import CollapsiblePanel from '../components/CollapsiblePanel.vue'
import MapToolbox from '../components/MapToolbox.vue'
import DronePanel from '../components/DronePanel.vue'
import SensorPanel from '../components/SensorPanel.vue'
import VideoPanel from '../components/VideoPanel.vue'
import AiCopilotBall from '../components/AiCopilotBall.vue'
import DeviceInfoDialog from '../components/DeviceInfoDialog.vue'
import DroneVideo from '../components/DroneVideo.vue'

const emit = defineEmits(['ws-status'])

/* ---------- 状态 ---------- */
const mapRef = ref(null)
const mapLoading = ref(true)
const overview = ref({})
const devices = ref([])          // 全量设备档案(含无人机与物联网设备)
const flyingMap = reactive({})
const selectedId = ref(null)
const detailVisible = ref(false)
const detail = ref(null)
const fencesRef = ref([])
const latestData = ref({})       // deviceId -> { fields, ts }
const deviceDlg = reactive({ visible: false, device: null })
const collapse = reactive({ drones: false, sensors: false, video: true })

/* 布局:右侧整栏统一收起 / 地图全屏 */
const sideCollapsed = ref(false)
const mapFullscreen = ref(false)

/* 地图工具箱:mapApi 就绪后传入;比例尺开启时抬升图例给原生控件让位 */
const toolboxMap = ref(null)
const legendLift = ref(false)

/* 轨迹回放:任一无人机(在线/离线)拉取历史上报逐帧重放 */
const replay = reactive({
  active: false, loading: false, deviceId: null, deviceCode: '',
  points: [], idx: 0, playing: false, speed: 8
})
let replayTimer = null
const replayOverlays = { full: null, done: null, marker: null }

const drones = computed(() => devices.value.filter((d) => d.category === 'DRONE'))
const cameraDevices = computed(() => devices.value.filter((d) => d.category === 'CAMERA'))
// 反制设备属于布防装备,不进传感面板(地图上按分类图标+范围圈展示)
const sensorDevices = computed(() =>
  devices.value.filter((d) => !['DRONE', 'CAMERA', ...Object.keys(COUNTER_META)].includes(d.category)))
const flyingList = computed(() => Object.values(flyingMap))

/* 弹窗实时数据:WS 每 2s 整体替换 flyingMap 条目对象,detail 若只在点击时赋值会冻结成旧快照,
   这里改为 computed 跟踪 —— 遥测数字与视频 HUD 始终跟随最新帧 */
const liveDetail = computed(() =>
  detail.value ? (flyingMap[detail.value.droneId] || detail.value) : null)
/** 无人机设备档案(取 videoUrl:配置了真实 HLS 流则回传真实画面,否则模拟图传) */
const detailDevice = computed(() =>
  devices.value.find((d) => d.id === liveDetail.value?.droneId) || null)

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
  drones: new Map(),    // droneId -> {marker,label,trackLine}
  devices: new Map(),   // deviceId -> {marker,label}(物联网设备)
  fences: []
}
const providerName = ref(getProviderMeta(getProviderId())?.name || '百度地图')

/* ---------- 初始化 ---------- */
onMounted(async () => {
  await Promise.all([loadOverview(), loadFences(), loadDevices(), loadLatestData()])
  initMap()
  connectWs()
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  stopReplay()
  closeWs()
  if (mapApi) mapApi.destroy()
})

function onKeydown(e) {
  if (e.key === 'Escape' && mapFullscreen.value) toggleFullscreen()
}

/* ---------- 右栏整体收起 / 地图全屏 ---------- */
function toggleSide() {
  sideCollapsed.value = !sideCollapsed.value
  nextTick(() => mapApi?.resize())
}

function toggleFullscreen() {
  mapFullscreen.value = !mapFullscreen.value
  nextTick(() => mapApi?.resize())
}

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
async function loadDevices() {
  try { devices.value = await http.get('/devices') } catch (e) { /* noop */ }
}
/** 各设备最近一帧(面板初值;运行期由 WS deviceData 增量维护) */
async function loadLatestData() {
  try {
    const rows = await http.get('/devices/latest-data')
    const map = {}
    for (const r of rows || []) {
      let fields = {}
      try { fields = JSON.parse(r.fieldsJson || '{}') } catch (e) { /* skip */ }
      map[r.deviceId] = { fields, ts: r.ts }
    }
    latestData.value = map
  } catch (e) { /* noop */ }
}

/* ---------- 地图 ---------- */
/* 3D 视角:百度 tilt73/heading64.5,高德 pitch55,天地图 2D 自动忽略 */
const view3d = ref(true)

function initMap() {
  createMap(mapRef.value, { center: { lng: 116.410, lat: 39.910 }, zoom: 12, view3d: view3d.value })
    .then((api) => {
      mapApi = api
      window.__mapApi = api        // E2E/调试钩子
      mapLoading.value = false
      drawFences(fencesRef.value)
      drawDeviceMarkers()
      toolboxMap.value = api
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
    const parts = parseFenceShapes(f)   // 单形状/复合统一为部件列表
    if (!parts.length) return
    const color = colors[f.type] || '#155eef'

    parts.forEach((p, idx) => {
      const points = p.points
      if (p.shape === 'CIRCLE' && p.radius) {
        overlays.fences.push(mapApi.addCircle(points[0], p.radius, { color, dashed: true, fillOpacity: 0.1 }))
        if (idx === 0) addFenceLabel(points[0], f, color)
      } else if (p.shape === 'LINE' && points.length >= 2 && p.radius) {
        // 线状走廊:中心线 + 半透明缓冲带
        overlays.fences.push(mapApi.addPolyline(points, { color, weight: 3, opacity: 0.9 }))
        overlays.fences.push(mapApi.addPolyline(points, { color, weight: Math.max(2, Math.round(p.radius / 60)), opacity: 0.18 }))
        if (idx === 0) addFenceLabel(points[Math.floor(points.length / 2)], f, color)
      } else if (points.length >= 3) {
        overlays.fences.push(mapApi.addPolygon(points, { color, fillOpacity: 0.1 }))
        if (idx === 0) addFenceLabel(points[Math.floor(points.length / 2)], f, color)
      }
    })
  })
}

function addFenceLabel(pos, fence, color) {
  const typeName = { NO_FLY: '禁飞区', LIMIT: '限飞区', WORK: '作业区' }[fence.type] || '围栏'
  const shapeName = { CIRCLE: '', LINE: ' · 走廊', POLYGON: '', MULTI: ` · ${parseFenceShapes(fence).length} 区域` }[fence.shape] || ''
  const html = `<b>${fence.name}</b><br/><span style="opacity:.75">${typeName}${shapeName}${fence.type === 'LIMIT' ? ' · 限高' + fence.maxAltitude + 'm' : ''}</span>`
  const label = mapApi.addLabel(pos, html, {
    dx: -70, dy: -18,
    css: { ...FENCE_LABEL_CSS, border: `1.5px solid ${color}` }
  })
  overlays.fences.push(label)
}

/** 停机坪已并入设备 ICON 层:无人机(含离线)在归航点按配置/分类图标展示 */

function statusText(s) {
  return { IDLE: '待命', FLYING: '飞行中', CHARGING: '充电中', MAINTENANCE: '维保中', OFFLINE: '离线' }[s] || s
}

/* 物联网设备分类图标 */
const DEVICE_LABEL_CSS = (color) => ({
  background: 'rgba(255,255,255,0.94)',
  color, fontSize: '11px', fontWeight: 600, padding: '1px 7px', borderRadius: '9px',
  whiteSpace: 'nowrap', boxShadow: '0 2px 6px -2px rgba(16,24,40,.25)',
  border: `1px solid ${color}55`
})

/**
 * 设备 ICON 层(全部设备,含无人机):
 * - 优先使用「设备管理」配置的自定义图标,未配置按分类默认(在线脉冲/离线灰)
 * - 无人机(含离线)在归航点展示;在飞时让位给实时移动图标,落地自动恢复
 * - 点击查看设备详情与历史曲线
 */
function drawDeviceMarkers() {
  if (!mapApi) return
  const seen = new Set()
  for (const d of devices.value) {
    if (!d.homeLng || !d.homeLat) continue
    if (d.category === 'DRONE' && d.status === 'FLYING') continue
    seen.add(d.id)
    const online = ['ONLINE', 'FLYING', 'IDLE', 'CHARGING'].includes(d.status)
    const kind = online ? 'online' : 'offline'
    const iconKey = d.icon || ''
    let entry = overlays.devices.get(d.id)
    if (entry && (entry.kind !== kind || entry.iconKey !== iconKey)) {
      entry.marker.destroy()
      entry.label.destroy()
      if (entry.range) mapApi.remove(entry.range)
      overlays.devices.delete(d.id)
      entry = null
    }
    if (!entry) {
      const marker = mapApi.addMarker({ lng: d.homeLng, lat: d.homeLat }, {
        svg: resolveDeviceIcon(d, { online }), size: 44,
        onClick: () => openDevice(d)
      })
      const label = mapApi.addLabel({ lng: d.homeLng, lat: d.homeLat }, d.code,
        { dx: 20, dy: 26, css: DEVICE_LABEL_CSS('#475467'), onClick: () => openDevice(d) })
      // 反制设备:按 scanRange 画虚线扫描范围圈(颜色取分类色)
      let range = null
      if (COUNTER_META[d.category] && d.scanRange) {
        range = mapApi.addCircle({ lng: d.homeLng, lat: d.homeLat }, d.scanRange, {
          color: deviceMeta(d.category).color, dashed: true, weight: 1.5,
          fillOpacity: 0.05, opacity: 0.6
        })
      }
      overlays.devices.set(d.id, { marker, label, range, kind, iconKey })
    }
  }
  // 清理已删除/已起飞设备
  for (const [id, entry] of overlays.devices) {
    if (!seen.has(id)) {
      entry.marker.destroy()
      entry.label.destroy()
      if (entry.range) mapApi.remove(entry.range)
      overlays.devices.delete(id)
    }
  }
}

function openDevice(d) {
  deviceDlg.device = d
  deviceDlg.visible = true
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
    const dev = drones.value.find((x) => x.id === t.droneId)
    const custom = customDeviceIcon(dev)                 // 仅用户上传图标不随航向旋转,预设/默认保持旋转
    const marker = mapApi.addMarker({ lng: t.lng, lat: t.lat }, {
      svg: custom || undefined,
      size: custom ? 44 : undefined,
      onClick: () => {
        selectedId.value = t.droneId
        detail.value = flyingMap[t.droneId]
        detailVisible.value = true
      }
    })
    const label = mapApi.addLabel({ lng: t.lng, lat: t.lat }, t.droneCode, {
      dx: 16, dy: -12, css: DRONE_LABEL_CSS,
      onClick: () => {
        selectedId.value = t.droneId
        detail.value = flyingMap[t.droneId]
        detailVisible.value = true
      }
    })
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

/* ---------- 轨迹回放 ----------
 * 数据源:/devices/{id}/history(在线机=模拟器实时入库,离线机=种子历史轨迹)
 * 地图叠加:灰色虚线全轨迹 + 紫色已飞段 + 移动无人机图标,控制条支持拖动/倍速
 */
async function startReplay(d) {
  if (replay.active && replay.deviceId === d.id) {
    stopReplay()
    return
  }
  stopReplay()
  replay.loading = true
  try {
    const res = await http.get(`/devices/${d.id}/history`,
      { params: { minutes: 4320, limit: 2000 } })
    const pts = (res?.items || [])
      .map((i) => ({
        lng: i.fields?.lng, lat: i.fields?.lat,
        heading: i.fields?.heading, altitude: i.fields?.altitude, ts: i.ts
      }))
      .filter((p) => p.lng != null && p.lat != null)
    if (pts.length < 2) {
      ElMessage.warning(`${d.code} 暂无历史轨迹数据`)
      return
    }
    replay.active = true
    replay.deviceId = d.id
    replay.deviceCode = d.code
    replay.points = pts
    replay.idx = 0
    replay.playing = true
    drawReplayOverlays()
    mapApi?.flyTo(pts[0], 13)
    restartReplayTimer()
  } catch (e) {
    /* 请求失败提示已由 axios 拦截器统一弹出 */
  } finally {
    replay.loading = false
  }
}

function drawReplayOverlays() {
  if (!mapApi) return
  // 回放移动图标遵循设备图标配置:自定义图片静态展示,预设/默认随航向旋转
  const dev = devices.value.find((x) => x.id === replay.deviceId)
  const custom = customDeviceIcon(dev)
  replayOverlays.full = mapApi.addPolyline(replay.points,
    { color: '#98a2b3', weight: 2.5, opacity: 0.55, dashed: true })
  replayOverlays.done = mapApi.addPolyline([replay.points[0]],
    { color: '#7c3aed', weight: 4, opacity: 0.9 })
  replayOverlays.marker = mapApi.addMarker(replay.points[0],
    { svg: custom || undefined, size: custom ? 44 : undefined })
  updateReplayFrame()
}

function updateReplayFrame() {
  const p = replay.points[replay.idx]
  if (!p) return
  replayOverlays.marker?.update({ lng: p.lng, lat: p.lat, heading: p.heading || 0 })
  replayOverlays.done?.setPath(replay.points.slice(0, replay.idx + 1))
}

function toggleReplayPlay() {
  if (!replay.playing && replay.idx >= replay.points.length - 1) {
    replay.idx = 0        // 播完再按播放:从头重放
  }
  replay.playing = !replay.playing
  restartReplayTimer()
}

function restartReplayTimer() {
  if (replayTimer) { clearInterval(replayTimer); replayTimer = null }
  if (!replay.playing) return
  const stepMs = Math.max(40, 500 / replay.speed)   // 1x = 0.5s/帧,16x ≈ 31ms(下限 40ms)
  replayTimer = setInterval(() => {
    if (replay.idx >= replay.points.length - 1) {
      replay.playing = false
      clearInterval(replayTimer)
      replayTimer = null
      return
    }
    replay.idx++
    updateReplayFrame()
  }, stepMs)
}

function onReplaySeek(v) {
  replay.idx = v
  updateReplayFrame()
}

function onReplaySpeed() {
  restartReplayTimer()
}

function stopReplay() {
  replay.active = false
  replay.playing = false
  replay.deviceId = null
  if (replayTimer) { clearInterval(replayTimer); replayTimer = null }
  replayOverlays.full?.destroy()
  replayOverlays.done?.destroy()
  replayOverlays.marker?.destroy()
  replayOverlays.full = replayOverlays.done = replayOverlays.marker = null
}

const replayTime = computed(() => {
  const ts = replay.points[replay.idx]?.ts
  if (!ts) return '--'
  return String(ts).replace('T', ' ').slice(5, 19)
})

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
      const p = msg.payload
      if (msg.type === 'telemetry' && Array.isArray(p)) {
        let hasChange = false
        p.forEach((t) => {
          flyingMap[t.droneId] = t
          updateDroneMarker(t)
          hasChange = true
        })
        if (hasChange) {
          removeStaleMarkers()
          loadOverview()
        }
      } else if (msg.type === 'alert') {
        pushAlert(p)          // 实时告警已移至顶栏铃铛(全局告警中心)
        loadOverview()
        if (p.lng && mapApi) {
          flashAlertPoint(p)
        }
      } else if (msg.type === 'deviceData') {
        // 物联网设备遥测:更新面板最新数据(无人机遥测走 telemetry)
        if (p.deviceId && p.fields) {
          latestData.value = {
            ...latestData.value,
            [p.deviceId]: { fields: p.fields, ts: p.ts }
          }
        }
      } else if (msg.type === 'deviceStatus') {
        // 设备上下线:同步档案状态并刷新图标/面板
        const d = devices.value.find((x) => x.id === p.deviceId)
        if (d) {
          d.status = p.status || (p.online ? 'ONLINE' : 'OFFLINE')
          drawDeviceMarkers()
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

/* 地图全屏:脱离布局铺满视口,Esc 退出 */
.map-wrap.fs {
  position: fixed;
  inset: 0;
  z-index: 1500;
  border-radius: 0;
  border: none;
}

/* 顶栏图标按钮(全屏 / 右栏收起) */
.topbar-btn {
  pointer-events: auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px; height: 30px;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  color: #475467;
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: all .2s;
}
.topbar-btn:hover { color: #155eef; transform: translateY(-1px); }

/* 轨迹回放控制条 */
.replay-bar {
  position: absolute;
  left: 50%; transform: translateX(-50%);
  bottom: 14px;
  z-index: 6;
  display: flex;
  align-items: center;
  gap: 10px;
  width: min(640px, calc(100% - 28px));
  padding: 8px 14px;
  border-radius: 12px;
  box-shadow: var(--shadow-md);
}
.rp-title {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 12.5px; font-weight: 700; color: #101828;
  white-space: nowrap;
}
.rp-slider { flex: 1; min-width: 80px; }
.rp-time {
  font-family: Consolas, monospace;
  font-size: 12px; color: #475467;
  white-space: nowrap;
}
.rp-count {
  font-size: 11.5px; color: var(--text-dim);
  font-family: Consolas, monospace;
  white-space: nowrap;
}
.rp-speed { width: 76px; flex-shrink: 0; }

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

/* 2D/3D 视角切换(移到左上让位:右上角留给原生底图类型工具条) */
.view-toggle {
  position: absolute; top: 54px; left: 12px; z-index: 5;
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
  transition: bottom .25s;
}
/* 比例尺开启:图例上移,给引擎原生左下比例尺让位 */
.map-legend.lift { bottom: 52px; }
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

/* 侧栏:可滚动面板列(整栏可收起,收起后地图占满整行) */
.side { width: 342px; display: flex; flex-direction: column; gap: 12px; min-height: 0; overflow-y: auto; overflow-x: hidden; }
.side :deep(.side-panel) { flex-shrink: 0; }
/* 在线面板占据剩余空间;已收起(collapsed)时不保留最小高度,修掉「收起后仍占位」 */
.side :deep(.side-panel:first-child:not(.collapsed)) { flex: 1 0 auto; min-height: 300px; }

.title-badge {
  font-size: 11px; padding: 1px 8px; border-radius: 999px; font-weight: 700;
  background: #eff6ff; color: #155eef;
  border: 1px solid #d6e4ff;
}
.badge-red { background: #fef3f2; color: #f04438; border-color: #fee4e2; }

.empty {
  padding: 34px 0; text-align: center; color: var(--text-faint);
  font-size: 13px; line-height: 1.8; display: flex; flex-direction: column; align-items: center; gap: 6px;
}
.empty p { margin: 0; }
.empty-sub { font-size: 11.5px; opacity: 0.8; }

/* 详情 */
.detail-video { margin-bottom: 12px; }
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
