<template>
  <div v-if="map">
    <!-- 工具箱按钮(右上,原 2D/3D 切换位) -->
    <div class="toolbox-btn" :class="{ active: open }" title="地图工具箱" @click="open = !open">
      <el-icon :size="16"><SetUp /></el-icon>
    </div>

    <!-- 工具箱面板 -->
    <div v-show="open" class="toolbox-panel panel">
      <div class="tb-head">
        <span class="tb-title"><el-icon color="#155eef"><SetUp /></el-icon>地图工具箱</span>
        <el-icon class="tb-close" @click="open = false"><Close /></el-icon>
      </div>
      <div v-for="c in controlRows" :key="c.key" class="tb-row">
        <span class="tb-name"><el-icon :size="14"><component :is="c.icon" /></el-icon>{{ c.label }}</span>
        <el-tooltip :disabled="c.key !== 'compass3d' || map.supports3d"
                    content="当前底图引擎为 2D,不支持 3D 罗盘" placement="left">
          <span class="tb-switch">
            <el-switch v-model="state[c.key]" size="small"
                       :disabled="c.key === 'compass3d' && !map.supports3d"
                       @change="applyControl(c.key)" />
          </span>
        </el-tooltip>
      </div>
      <div class="tb-sep"></div>
      <div class="tb-measures">
        <span class="tb-btn" :class="{ on: mode === 'distance' }" @click="toggleMeasure('distance')">
          <el-icon :size="14"><Aim /></el-icon>测距
        </span>
        <span class="tb-btn" :class="{ on: mode === 'area' }" @click="toggleMeasure('area')">
          <el-icon :size="14"><Grid /></el-icon>面积测算
        </span>
      </div>
      <div class="tb-btn tb-clear" :class="{ disabled: !records.length }"
           @click="records.length && clearMeasures()">
        <el-icon :size="14"><Delete /></el-icon>清除测量
        <span v-if="records.length" class="tb-count">{{ records.length }}</span>
      </div>
    </div>

    <!-- 方向盘:四向平移(单击步进/长按连续)+ 中心复位视野 -->
    <div v-show="state.pan" class="dpad">
      <span class="dp-btn dp-up" title="向上平移" @mousedown.prevent="padDown('up')" @mouseup="padUp"
            @mouseleave="padUp" @touchstart.prevent="padDown('up')" @touchend="padUp">
        <el-icon :size="13"><ArrowUp /></el-icon></span>
      <span class="dp-btn dp-left" title="向左平移" @mousedown.prevent="padDown('left')" @mouseup="padUp"
            @mouseleave="padUp" @touchstart.prevent="padDown('left')" @touchend="padUp">
        <el-icon :size="13"><ArrowLeft /></el-icon></span>
      <span class="dp-btn dp-center" title="复位视野" @click="padReset"><el-icon :size="13"><Aim /></el-icon></span>
      <span class="dp-btn dp-right" title="向右平移" @mousedown.prevent="padDown('right')" @mouseup="padUp"
            @mouseleave="padUp" @touchstart.prevent="padDown('right')" @touchend="padUp">
        <el-icon :size="13"><ArrowRight /></el-icon></span>
      <span class="dp-btn dp-down" title="向下平移" @mousedown.prevent="padDown('down')" @mouseup="padUp"
            @mouseleave="padUp" @touchstart.prevent="padDown('down')" @touchend="padUp">
        <el-icon :size="13"><ArrowDown /></el-icon></span>
    </div>

    <!-- 鹰眼:自绘示意图(围栏/设备/无人机/视野框),点击定位 -->
    <div v-show="state.hawkEye" class="hawk-eye panel">
      <canvas ref="hawkRef" class="hawk-canvas" @click="onHawkClick"></canvas>
      <span class="hawk-tip">缩略视图 · 点击定位</span>
    </div>

    <!-- 测量操作提示条 -->
    <div v-if="mode" class="measure-hint panel">
      <el-icon color="#f79009"><Aim /></el-icon>
      <span class="mh-text">{{ mode === 'distance' ? '测距' : '面积测算' }}:单击加点 · 双击完成</span>
      <span class="mh-btn" @click="undoPoint">撤销</span>
      <span class="mh-btn" @click="finishNow">完成</span>
      <span class="mh-btn danger" @click="exitMeasure">退出</span>
    </div>
  </div>
</template>

<script setup>
/**
 * 地图工具箱(实时监控大屏):
 * - 比例尺 / 工具条(底图类型) / 3D罗盘:适配层统一 setControl(引擎原生控件)
 * - 方向盘:DOM 十字盘,panBy 单击步进 + 长按连续,中心复位视野
 * - 鹰眼:自绘 canvas 示意图(围栏三色/设备点/在飞无人机/视野框),点击 flyTo,
 *   四引擎行为一致(百度 GL 无原生鹰眼,且多实例有稳定性风险)
 * - 测距 / 面积测算:复用适配层 startDraw(DOM 事件绘制),实时标签跟随光标,
 *   双击定稿可连续测量,支持撤销/清除
 * 开关状态持久化 localStorage(wrj.map.toolbox),测量态不持久化
 */
import { ref, reactive, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { SetUp, Close, Odometer, Menu, Guide, View, Compass, Aim, Grid, Delete,
  ArrowUp, ArrowDown, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { distMeters, sphericalArea, circleOutline } from '../utils/mapAdapter'
import { parseFenceShapes } from '../utils/map'

const props = defineProps({
  map: { type: Object, default: null },       // mapApi(适配层实例),地图就绪前为 null
  fences: { type: Array, default: () => [] },
  devices: { type: Array, default: () => [] },
  flying: { type: Array, default: () => [] }
})
const emit = defineEmits(['scale'])   // (boolean) 通知父组件抬升图例,给原生比例尺让位

/* ---------- 开关状态(持久化) ---------- */
const LS_KEY = 'wrj.map.toolbox'
const KEYS = ['scale', 'mapType', 'pan', 'hawkEye', 'compass3d']

function loadState() {
  const s = { scale: false, mapType: false, pan: false, hawkEye: false, compass3d: false }
  try { Object.assign(s, JSON.parse(localStorage.getItem(LS_KEY) || '{}')) } catch (e) { /* 损坏回落默认 */ }
  KEYS.forEach((k) => { s[k] = !!s[k] })
  return s
}
const state = reactive(loadState())
const open = ref(false)
const persist = () => {
  try {
    localStorage.setItem(LS_KEY, JSON.stringify(Object.fromEntries(KEYS.map((k) => [k, state[k]]))))
  } catch (e) { /* 隐私模式等场景忽略 */ }
}

const controlRows = [
  { key: 'scale', label: '比例尺', icon: Odometer },
  { key: 'mapType', label: '工具条', icon: Menu },
  { key: 'pan', label: '方向盘', icon: Guide },
  { key: 'hawkEye', label: '显示鹰眼', icon: View },
  { key: 'compass3d', label: '3D罗盘', icon: Compass }
]

function applyControl(k) {
  if (!props.map) return
  if (k === 'hawkEye' || k === 'pan') {   // 纯前端 DOM 部件,无引擎调用
    persist()
    if (k === 'hawkEye') scheduleHawk()
    return
  }
  const ok = props.map.setControl(k, state[k])
  if (!ok && state[k]) {
    state[k] = false
    ElMessage.info('当前底图引擎不支持该控件')
  }
  persist()
  if (k === 'scale') emit('scale', state.scale)
}

/* 地图就绪:恢复持久化控件 + 订阅视野变化驱动鹰眼重绘 */
watch(() => props.map, (api) => {
  if (!api) return
  for (const k of ['scale', 'mapType', 'compass3d']) {
    if (state[k] && !api.setControl(k, true)) state[k] = false
  }
  if (state.scale) emit('scale', true)
  api.onViewChange(() => scheduleHawk())
  scheduleHawk()
}, { immediate: true })

/* ---------- 方向盘 ---------- */
const DIRS = { up: [0, -1], down: [0, 1], left: [-1, 0], right: [1, 0] }
let holdTimer = null
let repTimer = null

function pan(dir, px) {
  const [dx, dy] = DIRS[dir]
  props.map?.panBy(dx * px, dy * px)
}
function padDown(dir) {
  pan(dir, 80)                                   // 单击步进
  holdTimer = setTimeout(() => {                 // 长按 220ms 后连续平移
    repTimer = setInterval(() => pan(dir, 26), 55)
  }, 220)
}
function padUp() {
  clearTimeout(holdTimer)
  clearInterval(repTimer)
}
function padReset() { props.map?.resetView() }

/* ---------- 鹰眼(自绘示意图) ---------- */
const HAWK_W = 184
const HAWK_H = 128
const hawkRef = ref(null)
let rafHawk = 0
let proj = null   // 最近一次投影参数(点击逆投影用){ west, north, kx, scale, ox, oy, pad }

function scheduleHawk() {
  if (!state.hawkEye) return
  cancelAnimationFrame(rafHawk)
  rafHawk = requestAnimationFrame(drawHawk)
}

function drawHawk() {
  const api = props.map
  const cv = hawkRef.value
  if (!api || !cv) return
  const b = api.getBounds()
  if (!b) return

  // 世界范围 = 围栏 ∪ 设备归航点 ∪ 在飞无人机 ∪ 当前中心
  // (刻意不含视野 bounds:3D 高倾角下原生 bounds 延伸至地平线,会把世界框拉得过大、业务要素压成一条)
  const colors = { NO_FLY: '#f04438', LIMIT: '#f79009', WORK: '#12b76a' }
  const shapes = []
  const pts = [api.getCenter()].filter(Boolean)
  for (const f of props.fences) {
    if (!f.enabled) continue
    for (const part of parseFenceShapes(f)) {
      let ring = part.points || []
      if (part.shape === 'CIRCLE' && part.points?.[0] && part.radius) {
        ring = circleOutline(part.points[0], part.radius, 36)
      }
      if (ring.length < 2) continue
      shapes.push({ ring, color: colors[f.type] || '#155eef', line: part.shape === 'LINE' })
      pts.push(...ring)
    }
  }
  for (const d of props.devices) {
    if (d.homeLng && d.homeLat) pts.push({ lng: d.homeLng, lat: d.homeLat })
  }
  const drones = props.flying.filter((t) => t.lng && t.lat)
  drones.forEach((t) => pts.push(t))
  if (pts.length < 2) return

  const lngs = pts.map((p) => p.lng)
  const lats = pts.map((p) => p.lat)
  const w0 = Math.min(...lngs), w1 = Math.max(...lngs)
  const s0 = Math.min(...lats), n0 = Math.max(...lats)
  // 四周各扩 12%,防要素贴边
  const spanLng = (w1 - w0) || 0.01
  const spanLat = (n0 - s0) || 0.01
  const west = w0 - spanLng * 0.12, east = w1 + spanLng * 0.12
  const south = s0 - spanLat * 0.12, north = n0 + spanLat * 0.12

  const dpr = window.devicePixelRatio || 1
  if (cv.width !== HAWK_W * dpr) { cv.width = HAWK_W * dpr; cv.height = HAWK_H * dpr }
  const cw = HAWK_W, ch = HAWK_H
  const ctx = cv.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, cw, ch)

  // 等距圆柱投影:经度按平均纬度 cos 校正,保持横纵比例
  const kx = Math.max(0.1, Math.cos(((north + south) / 2) * Math.PI / 180))
  const pad = 6
  const sx = (east - west) * kx, sy = north - south
  const scale = Math.min((cw - 2 * pad) / (sx || 1), (ch - 2 * pad) / (sy || 1))
  const ox = (cw - 2 * pad - sx * scale) / 2
  const oy = (ch - 2 * pad - sy * scale) / 2
  const X = (lng) => pad + ox + (lng - west) * kx * scale
  const Y = (lat) => pad + oy + (north - lat) * scale
  proj = { west, north, kx, scale, ox, oy, pad }

  // 深色底 + 网格
  ctx.fillStyle = '#0b1322'
  ctx.fillRect(0, 0, cw, ch)
  ctx.strokeStyle = 'rgba(148,163,184,0.10)'
  ctx.lineWidth = 1
  for (let x = 24; x < cw; x += 24) { ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, ch); ctx.stroke() }
  for (let y = 24; y < ch; y += 24) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(cw, y); ctx.stroke() }

  // 围栏:线状走廊画折线,面/圆画填充轮廓
  for (const sh of shapes) {
    ctx.beginPath()
    sh.ring.forEach((p, i) => (i ? ctx.lineTo(X(p.lng), Y(p.lat)) : ctx.moveTo(X(p.lng), Y(p.lat))))
    if (!sh.line) ctx.closePath()
    ctx.strokeStyle = sh.color
    ctx.lineWidth = 1.2
    ctx.globalAlpha = 0.9
    ctx.stroke()
    if (!sh.line) {
      ctx.globalAlpha = 0.16
      ctx.fillStyle = sh.color
      ctx.fill()
    }
    ctx.globalAlpha = 1
  }

  // 设备点 / 在飞无人机(含航向短线)
  ctx.fillStyle = '#98a2b3'
  for (const d of props.devices) {
    if (!d.homeLng || !d.homeLat) continue
    ctx.beginPath()
    ctx.arc(X(d.homeLng), Y(d.homeLat), 2.2, 0, Math.PI * 2)
    ctx.fill()
  }
  for (const t of drones) {
    const x = X(t.lng), y = Y(t.lat)
    ctx.fillStyle = '#3b82f6'
    ctx.beginPath()
    ctx.arc(x, y, 3.5, 0, Math.PI * 2)
    ctx.fill()
    if (t.heading != null) {
      const a = ((t.heading - 90) * Math.PI) / 180
      ctx.strokeStyle = '#93c5fd'
      ctx.lineWidth = 1.5
      ctx.beginPath()
      ctx.moveTo(x, y)
      ctx.lineTo(x + Math.cos(a) * 8, y + Math.sin(a) * 8)
      ctx.stroke()
    }
  }

  // 当前视野矩形(3D 倾斜下 bounds 可远超世界框 → 钳制在画布内)
  const rx = X(b.sw.lng), ry = Y(b.ne.lat)
  const rw = Math.max(4, X(b.ne.lng) - rx), rh = Math.max(4, Y(b.sw.lat) - ry)
  const cl = (v, lo, hi) => Math.min(Math.max(v, lo), hi)
  const vx = cl(rx, 0, cw - 4), vy = cl(ry, 0, ch - 4)
  const vw = cl(rx + rw, 4, cw) - vx, vh = cl(ry + rh, 4, ch) - vy
  ctx.fillStyle = 'rgba(59,130,246,0.14)'
  ctx.fillRect(vx, vy, vw, vh)
  ctx.strokeStyle = '#7fb0ff'
  ctx.lineWidth = 1.4
  ctx.setLineDash([4, 3])
  ctx.strokeRect(vx, vy, vw, vh)
  ctx.setLineDash([])
}

function onHawkClick(e) {
  const api = props.map
  if (!api || !proj) return
  const rect = e.target.getBoundingClientRect()
  const x = e.clientX - rect.left
  const y = e.clientY - rect.top
  const lng = proj.west + (x - proj.pad - proj.ox) / (proj.kx * proj.scale)
  const lat = proj.north - (y - proj.pad - proj.oy) / proj.scale
  if (!isFinite(lng) || !isFinite(lat)) return
  api.flyTo({ lng, lat }, api.getZoom())
}

/* 数据变化驱动鹰眼重绘(WS 每 2s 一帧,rAF 已合并) */
watch(() => [props.fences, props.devices], scheduleHawk)
watch(() => props.flying, scheduleHawk)

/* ---------- 测距 / 面积测算 ---------- */
const mode = ref(null)        // 'distance' | 'area' | null(互斥)
const records = ref([])       // 已定稿测量 [{ overlays, label }]
let liveLabel = null          // 实时标签(单例复用)
let rafLive = 0

const DIST_CSS = {
  background: 'rgba(255,255,255,0.95)', color: '#b54708', fontSize: '11.5px', fontWeight: 700,
  padding: '3px 9px', borderRadius: '8px', whiteSpace: 'nowrap', pointerEvents: 'none',
  fontFamily: 'inherit', boxShadow: '0 2px 8px -2px rgba(180,83,9,.45)'
}
const AREA_CSS = { ...DIST_CSS, color: '#6941c6', boxShadow: '0 2px 8px -2px rgba(105,65,198,.45)' }

const pathLength = (pts) => pts.reduce((s, p, i) => (i ? s + distMeters(pts[i - 1], p) : 0), 0)
const centroid = (pts) => ({
  lng: pts.reduce((s, p) => s + p.lng, 0) / pts.length,
  lat: pts.reduce((s, p) => s + p.lat, 0) / pts.length
})
const fmtDist = (v) => (v < 1000 ? `${Math.round(v)} m` : `${(v / 1000).toFixed(2)} km`)
const fmtArea = (v) => (v < 1e6 ? `${Math.round(v).toLocaleString()} m²` : `${(v / 1e6).toFixed(2)} km²`)

function toggleMeasure(next) {
  if (!props.map) return
  if (mode.value === next) { exitMeasure(); return }
  if (mode.value) {
    // 互斥切换:先定稿当前测量(无效形状由随后的 startDraw 内部 stopDraw 重置)
    try { props.map.finishDraw(true) } catch (e) { /* ignore */ }
  }
  mode.value = next
  props.map.startDraw({
    shape: next === 'distance' ? 'LINE' : 'POLYGON',
    keep: true,   // 完成后保留结果继续下一段测量
    onUpdate: (pts, r, cursor) => updateLive(next, pts, cursor),
    onFinish: (pts, r, bundle) => commitRecord(next, pts, bundle)
  })
}

function updateLive(m, pts, cursor) {
  cancelAnimationFrame(rafLive)
  rafLive = requestAnimationFrame(() => renderLive(m, pts, cursor))
}

function renderLive(m, pts, cursor) {
  const api = props.map
  if (!api || !mode.value) return
  if (!pts.length) return hideLive()
  let text, anchor
  if (m === 'distance') {
    let total = pathLength(pts)
    if (cursor) total += distMeters(pts[pts.length - 1], cursor)
    text = `总长 ${fmtDist(total)}`
    anchor = cursor || pts[pts.length - 1]
  } else {
    const ring = cursor ? [...pts, cursor] : pts
    text = ring.length >= 3
      ? `面积 ${fmtArea(sphericalArea(ring))} · 周长 ${fmtDist(pathLength([...ring, ring[0]]))}`
      : `再点 ${3 - pts.length} 点起算`
    anchor = cursor || pts[pts.length - 1]
  }
  if (!liveLabel) {
    liveLabel = api.addLabel(anchor, text, { dx: 14, dy: -26, css: m === 'distance' ? DIST_CSS : AREA_CSS })
  } else {
    liveLabel.setPosition(anchor)
    liveLabel.setContent(text)
  }
}

function commitRecord(m, pts, bundle) {
  hideLive()
  const overlays = [...(bundle || [])]
  let anchor, text
  if (m === 'distance') {
    anchor = pts[pts.length - 1]
    text = `总长 ${fmtDist(pathLength(pts))}`
  } else {
    overlays.push(props.map.addPolygon(pts, { color: '#7c3aed', weight: 2, fillOpacity: 0.14 }))
    anchor = centroid(pts)
    text = `面积 ${fmtArea(sphericalArea(pts))} · 周长 ${fmtDist(pathLength([...pts, pts[0]]))}`
  }
  records.value.push({
    overlays,
    label: props.map.addLabel(anchor, text, { dx: 12, dy: -24, css: m === 'distance' ? DIST_CSS : AREA_CSS })
  })
}

function undoPoint() { props.map?.undoDrawPoint() }

function finishNow() {
  if (!mode.value) return
  try { props.map.finishDraw(true) } catch (e) { /* 点数不足时静默 */ }
}

function hideLive() {
  liveLabel?.destroy()
  liveLabel = null
}

function exitMeasure() {
  if (!mode.value) return
  try { props.map.stopDraw() } catch (e) { /* ignore */ }
  hideLive()
  mode.value = null
}

function clearMeasures() {
  exitMeasure()
  records.value.forEach((r) => {
    r.overlays.forEach((o) => props.map?.remove(o))
    r.label?.destroy()
  })
  records.value = []
}

/* ---------- Esc:测量中退出测量,否则收起面板 ---------- */
function onKeydown(e) {
  if (e.key !== 'Escape') return
  if (mode.value) { exitMeasure(); return }
  if (open.value) open.value = false
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  clearMeasures()
  cancelAnimationFrame(rafHawk)
  cancelAnimationFrame(rafLive)
  padUp()
})
</script>

<style scoped>
/* 工具箱按钮 */
.toolbox-btn {
  position: absolute; right: 12px; top: 54px; z-index: 15;
  width: 30px; height: 30px; border-radius: 9px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  color: #475467;
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: all .2s;
}
.toolbox-btn:hover { color: #155eef; transform: translateY(-1px); }
.toolbox-btn.active {
  background: #155eef; color: #fff;
  box-shadow: 0 2px 8px -2px rgba(21, 94, 239, .55);
}

/* 工具箱面板 */
.toolbox-panel {
  position: absolute; right: 12px; top: 92px; z-index: 15;
  width: 208px;
  padding: 10px 12px;
  border-radius: 12px;
  box-shadow: var(--shadow-md);
}
.tb-head {
  display: flex; align-items: center; justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
}
.tb-title {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 13px; font-weight: 700; color: #101828;
}
.tb-close { cursor: pointer; color: var(--text-dim); }
.tb-close:hover { color: #f04438; }
.tb-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 7px 0;
}
.tb-name {
  display: inline-flex; align-items: center; gap: 7px;
  font-size: 12.5px; color: #344054;
}
.tb-switch { display: inline-flex; }
.tb-sep { height: 1px; background: var(--border); margin: 4px 0; }
.tb-measures { display: flex; gap: 8px; padding: 8px 0 2px; }
.tb-btn {
  flex: 1;
  display: inline-flex; align-items: center; justify-content: center; gap: 5px;
  padding: 6px 0;
  border-radius: 8px;
  font-size: 12.5px; color: #344054;
  background: #f2f4f7;
  cursor: pointer;
  transition: all .18s;
  user-select: none;
  white-space: nowrap;
}
.tb-btn:hover { color: #155eef; background: #eff6ff; }
.tb-btn.on {
  background: #f79009; color: #fff; font-weight: 700;
  box-shadow: 0 2px 8px -2px rgba(247, 144, 9, .55);
}
.tb-clear {
  flex: none; margin-top: 8px;
  background: transparent;
  border: 1px dashed var(--border);
}
.tb-clear:hover { color: #d92d20; border-color: #fda29b; background: #fef3f2; }
.tb-clear.disabled { opacity: .45; cursor: not-allowed; }
.tb-clear.disabled:hover { color: #344054; border-color: var(--border); background: transparent; }
.tb-count {
  font-size: 10.5px; line-height: 16px;
  padding: 0 6px;
  border-radius: 999px;
  background: #f04438; color: #fff;
}

/* 方向盘 */
.dpad {
  position: absolute; right: 20px; bottom: 190px; z-index: 5;
  display: grid;
  grid-template-columns: repeat(3, 28px);
  grid-template-rows: repeat(3, 28px);
  gap: 3px;
  padding: 5px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-radius: 12px;
  box-shadow: var(--shadow-sm);
}
.dp-up { grid-area: 1 / 2; }
.dp-left { grid-area: 2 / 1; }
.dp-center { grid-area: 2 / 2; }
.dp-right { grid-area: 2 / 3; }
.dp-down { grid-area: 3 / 2; }
.dp-btn {
  display: flex; align-items: center; justify-content: center;
  border-radius: 8px;
  background: #f2f4f7;
  color: #475467;
  cursor: pointer;
  transition: all .15s;
  user-select: none;
}
.dp-btn:hover { color: #155eef; background: #eff6ff; }
.dp-btn:active { transform: scale(.92); background: #d6e4ff; }
.dp-center {
  background: #eef4ff;
  color: #155eef;
  border: 1px solid #d6e4ff;
}

/* 鹰眼 */
.hawk-eye {
  position: absolute; left: 12px; top: 96px; z-index: 5;
  display: flex; flex-direction: column; gap: 3px;
  padding: 4px;
  border-radius: 10px;
}
.hawk-canvas {
  width: 184px; height: 128px;
  border-radius: 7px;
  cursor: pointer;
  display: block;
}
.hawk-tip {
  font-size: 10px; line-height: 1;
  color: var(--text-dim);
  text-align: center;
  letter-spacing: 1px;
  padding-bottom: 2px;
}

/* 测量提示条 */
.measure-hint {
  position: absolute; top: 54px; left: 50%;
  transform: translateX(-50%);
  z-index: 15;
  display: flex; align-items: center; gap: 10px;
  padding: 7px 14px;
  border-radius: 10px;
  box-shadow: var(--shadow-md);
}
.mh-text { font-size: 12.5px; font-weight: 600; color: #344054; white-space: nowrap; }
.mh-btn {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 6px;
  background: #eff6ff;
  color: #155eef;
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
}
.mh-btn:hover { background: #d6e4ff; }
.mh-btn.danger { color: #d92d20; background: #fef3f2; }
.mh-btn.danger:hover { background: #fee4e2; }
</style>
