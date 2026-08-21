<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="光电视窗 · 双光谱跟踪"
    width="600px"
    append-to-body
    destroy-on-close
    class="eo-dialog"
  >
    <div class="eo-wrap">
      <div class="eo-bar">
        <span class="eo-name">{{ eo?.name || '光电跟踪' }}<i class="eo-code">{{ eo?.code }}</i></span>
        <el-radio-group v-model="mode" size="small">
          <el-radio-button value="white">白光</el-radio-button>
          <el-radio-button value="ir">热成像</el-radio-button>
        </el-radio-group>
        <el-radio-group v-model="zoom" size="small">
          <el-radio-button :value="1">1×</el-radio-button>
          <el-radio-button :value="2">2×</el-radio-button>
          <el-radio-button :value="4">4×</el-radio-button>
        </el-radio-group>
      </div>

      <canvas
        ref="canvasRef"
        :width="W"
        :height="H"
        class="eo-canvas"
        @click="onClickCanvas"
      />

      <div class="eo-ops">
        <template v-if="selected">
          <div class="eo-tgt">
            <b :style="{ color: STATUS_COLOR[selected.status] || '#ff5c5c' }">{{ selected.id }}</b>
            <span>{{ selected.kind === 'FAST' ? '快速穿越机' : '侦察巡飞机' }}</span>
            <span>高 {{ selected.alt }}m</span>
            <span>{{ selected.tracked ? '已跟踪' : '未跟踪' }}</span>
          </div>
          <el-button v-if="lockedId !== selected.id" type="primary" size="small" @click="lockSelected">
            <el-icon><Aim /></el-icon>&nbsp;锁定目标
          </el-button>
          <el-button v-else size="small" @click="lockedId = null">解锁</el-button>
          <el-button
            v-for="c in capableCounters"
            :key="c.deviceId"
            size="small"
            :type="c.action === 'DESTROY' ? 'danger' : 'warning'"
            @click="$emit('engage', { deviceId: c.deviceId, enemyId: selected.id })"
          >
            {{ c.name }} · {{ ACTION_TEXT[c.action] }}
          </el-button>
        </template>
        <span v-else class="eo-hint">点击视场内目标框选中后可锁定 / 处置</span>
        <span class="eo-hint right">{{ inRange.length }} 目标在探测范围({{ eo?.scanRange }}m)内</span>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
/**
 * 光电视窗(攻防演练 Req4):双光谱(白光/热成像)+ 数字变倍 canvas HUD,
 * 目标按相对方位/俯仰角投影进视场;点击选框 → 锁定(视轴跟随)→ 联动反制装备处置。
 * 纯展示+交互组件:目标数据来自演练快照,处置动作 emit 给 Drill 页调 REST。
 */
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { Aim } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { distMeters } from '../utils/mapAdapter'

const props = defineProps({
  modelValue: Boolean,
  /** 光电装备布防点 {deviceId,code,name,lng,lat,scanRange,...} */
  eo: { type: Object, default: null },
  /** 演练敌机快照数组 */
  enemies: { type: Array, default: () => [] },
  /** 反制类布防数组(处置按钮) */
  counters: { type: Array, default: () => [] }
})
defineEmits(['update:modelValue', 'engage'])

const W = 552
const H = 330
const STATUS_COLOR = {
  FLYING: '#ff5c5c', JAMMED: '#f79009', CAPTURING: '#b39dff',
  NEUTRALIZED: '#8da2b5', ESCAPED: '#667085'
}
const ACTION_TEXT = { JAM: '压制驱离', DESTROY: '激光击落', CAPTURE: '网捕捕获' }

const canvasRef = ref(null)
const mode = ref('white')
const zoom = ref(1)
const lockedId = ref(null)
const selectedId = ref(null)
let raf = 0
let boresight = 0          // 视轴方位角(度)
let t0 = Date.now()

/** 探测范围内的目标 + 方位/距离缓存 */
const inRange = computed(() => {
  if (!props.eo) return []
  const eo = props.eo
  return (props.enemies || [])
    .filter((e) => !['NEUTRALIZED', 'ESCAPED'].includes(e.status))
    .map((e) => {
      const d = distMeters({ lng: eo.lng, lat: eo.lat }, e)
      const brg = (Math.atan2(e.lng - eo.lng, e.lat - eo.lat) * 180 / Math.PI + 360) % 360
      return { e, dist: d, bearing: brg }
    })
    .filter((t) => t.dist <= (eo.scanRange || 2500))
})

const selected = computed(() => inRange.value.find((t) => t.e.id === selectedId.value)?.e || null)
const lockedTgt = computed(() => inRange.value.find((t) => t.e.id === lockedId.value) || null)

/** 锁定目标可用的反制装备(在其范围内 + 非冷却;激光需已跟踪) */
const capableCounters = computed(() => {
  const t = lockedTgt.value || inRange.value.find((x) => x.e.id === selectedId.value)
  if (!t) return []
  return props.counters.filter((c) => {
    if (c.cooling) return false
    const d = distMeters(c, t.e)
    if (d > c.scanRange) return false
    if (c.action === 'DESTROY' && !t.e.tracked) return false
    return true
  })
})

function lockSelected() {
  if (!selected.value) return
  // 锁定即引导:目标须在光电跟踪范围内(超出即脱锁)
  lockedId.value = selected.value.id
  ElMessage.success(`已锁定 ${selected.value.id},视轴跟随中`)
}

/* ---------- canvas 投影:方位/俯仰 → 视场像素 ---------- */
const FOV = computed(() => 44 / zoom.value)   // 水平视场角(度)

function project(t) {
  const rel = ((t.bearing - boresight + 540) % 360) - 180
  const halfFov = FOV.value / 2
  const elev = Math.atan2(t.e.alt || 0, Math.max(1, t.dist)) * 180 / Math.PI
  const x = W / 2 + (rel / halfFov) * (W / 2 - 26)
  const y = H / 2 - (elev / halfFov) * (H / 2 - 26)
  return { x, y, rel, elev }
}

function hitTest(px, py) {
  for (const t of [...inRange.value].reverse()) {
    const p = project(t)
    if (p.x < -20 || p.x > W + 20) continue
    const s = boxSize(t.dist)
    if (Math.abs(px - p.x) < s / 2 + 6 && Math.abs(py - p.y) < s / 2 + 6) return t.e.id
  }
  return null
}

function boxSize(dist) {
  return Math.max(16, Math.min(64, 1400 / Math.max(60, dist) * 42))
}

function onClickCanvas(ev) {
  const r = ev.target.getBoundingClientRect()
  const id = hitTest((ev.clientX - r.left) * (W / r.width), (ev.clientY - r.top) * (H / r.height))
  if (id) {
    selectedId.value = id
    lockedId.value = id
  }
}

/* ---------- HUD 渲染循环 ---------- */
function draw() {
  const cv = canvasRef.value
  if (!cv) return
  const ctx = cv.getContext('2d')
  const t = (Date.now() - t0) / 1000
  const ir = mode.value === 'ir'

  // 视轴:锁定 → 平滑跟随目标方位;搜索 → 缓动指向最近目标(入界即可点选);无目标 6°/s 环扫
  if (lockedTgt.value) {
    const want = lockedTgt.value.bearing
    let d = ((want - boresight + 540) % 360) - 180
    boresight += d * 0.08
  } else {
    const near = [...inRange.value].sort((a, b) => a.dist - b.dist)[0]
    if (near) {
      const d = ((near.bearing - boresight + 540) % 360) - 180
      boresight = (boresight + Math.sign(d) * Math.min(Math.abs(d), 0.5) + 360) % 360
    } else {
      boresight = (boresight + 0.1 + Math.sin(t / 6) * 0.3 + 360) % 360
    }
  }

  // 背景(白光=夜视绿 / 热成像=暖黑)
  ctx.fillStyle = ir ? '#160f08' : '#08140d'
  ctx.fillRect(0, 0, W, H)

  // 噪点颗粒(热成像更粗)
  const n = ir ? 260 : 150
  for (let i = 0; i < n; i++) {
    ctx.fillStyle = `rgba(${ir ? '255,200,140' : '150,255,170'},${Math.random() * 0.1})`
    ctx.fillRect(Math.random() * W, Math.random() * H, ir ? 2 : 1, ir ? 2 : 1)
  }
  // 扫描线
  ctx.fillStyle = 'rgba(0,0,0,0.16)'
  for (let y = 0; y < H; y += 3) ctx.fillRect(0, y, W, 1)

  // 方位刻度尺(顶部)
  ctx.strokeStyle = ir ? '#ffb26b' : '#7dffa0'
  ctx.fillStyle = ir ? '#ffb26b' : '#7dffa0'
  ctx.font = '10px Consolas, monospace'
  ctx.textAlign = 'center'
  const halfFov = FOV.value / 2
  const pxPerDeg = (W - 52) / FOV.value
  for (let d = Math.ceil((boresight - halfFov) / 5) * 5; d <= boresight + halfFov; d += 5) {
    const x = W / 2 + (((d - boresight + 540) % 360) - 180) * pxPerDeg
    if (x < 20 || x > W - 20) continue
    const major = ((d % 10) + 10) % 10 === 0
    ctx.globalAlpha = major ? 0.9 : 0.4
    ctx.beginPath()
    ctx.moveTo(x, 20)
    ctx.lineTo(x, major ? 30 : 25)
    ctx.stroke()
    if (major) ctx.fillText(`${((d % 360) + 360) % 360}°`, x, 42)
  }
  ctx.globalAlpha = 1

  // 中心十字 + 视轴圆
  ctx.strokeStyle = ir ? '#ffcf9e' : '#a8ffc2'
  ctx.setLineDash([6, 6])
  ctx.beginPath()
  ctx.moveTo(W / 2, 0); ctx.lineTo(W / 2, H)
  ctx.moveTo(0, H / 2); ctx.lineTo(W, H / 2)
  ctx.stroke()
  ctx.setLineDash([])
  ctx.beginPath()
  ctx.arc(W / 2, H / 2, 26, 0, Math.PI * 2)
  ctx.stroke()

  // 目标框
  for (const tgt of inRange.value) {
    const p = project(tgt)
    if (p.x < 10 || p.x > W - 10 || p.y < 10 || p.y > H - 10) continue
    const s = boxSize(tgt.dist)
    const col = STATUS_COLOR[tgt.e.status] || '#ff5c5c'
    const locked = tgt.e.id === lockedId.value
    const sel = tgt.e.id === selectedId.value
    ctx.strokeStyle = col
    ctx.lineWidth = locked || sel ? 2 : 1.2
    if (locked) {
      // 锁定动效:四角括号收缩脉动
      const k = s / 2 + 5 + Math.sin(t * 6) * 3
      ctx.beginPath()
      for (const [sx, sy] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) {
        ctx.moveTo(p.x + sx * k, p.y + sy * k - sy * 10)
        ctx.lineTo(p.x + sx * k, p.y + sy * k)
        ctx.lineTo(p.x + sx * k - sx * 10, p.y + sy * k)
      }
      ctx.stroke()
      ctx.fillStyle = col
      ctx.font = 'bold 11px Consolas, monospace'
      ctx.fillText(`LOCK ${tgt.e.id}`, p.x, p.y - k - 6)
    } else {
      ctx.strokeRect(p.x - s / 2, p.y - s / 2, s, s)
    }
    ctx.fillStyle = col
    ctx.font = '10px Consolas, monospace'
    ctx.textAlign = 'left'
    ctx.fillText(`${tgt.e.id} ${Math.round(tgt.dist)}m H${tgt.e.alt}`, p.x + s / 2 + 5, p.y - s / 2 + 10)
    ctx.textAlign = 'center'
  }

  // HUD 底栏
  ctx.fillStyle = ir ? '#ffcf9e' : '#a8ffc2'
  ctx.font = '11px Consolas, monospace'
  ctx.textAlign = 'left'
  ctx.fillText(`MODE ${ir ? 'IR-热成像' : 'EO-白光'}  ZOOM ${zoom.value}x  FOV ${FOV.value.toFixed(1)}°`, 12, H - 12)
  ctx.textAlign = 'right'
  ctx.fillText(`AZ ${Math.round(boresight)}°  ${lockedTgt.value ? 'TRACKING ' + lockedTgt.value.e.id : 'SCAN'}`, W - 12, H - 12)

  // 视场边缘暗角
  const grad = ctx.createRadialGradient(W / 2, H / 2, H / 3, W / 2, H / 2, W / 1.2)
  grad.addColorStop(0, 'rgba(0,0,0,0)')
  grad.addColorStop(1, 'rgba(0,0,0,0.55)')
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, W, H)

  // 无目标提示
  if (!inRange.value.length) {
    ctx.fillStyle = 'rgba(255,255,255,0.55)'
    ctx.font = '13px sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText('视场内暂无目标 · 等待敌机进入探测范围', W / 2, H / 2 - 40)
  }

  raf = requestAnimationFrame(draw)
}

watch(() => props.modelValue, (open) => {
  cancelAnimationFrame(raf)
  if (open) {
    boresight = 0
    t0 = Date.now()
    raf = requestAnimationFrame(draw)
  }
})
watch(() => props.enemies, () => {
  // 目标列表变化后校正选中/锁定引用(超范围自动脱锁)
  const ids = new Set(inRange.value.map((t) => t.e.id))
  if (selectedId.value && !ids.has(selectedId.value)) selectedId.value = null
  if (lockedId.value && !ids.has(lockedId.value)) lockedId.value = null
})
onBeforeUnmount(() => cancelAnimationFrame(raf))
</script>

<style scoped>
.eo-wrap { display: flex; flex-direction: column; gap: 10px; }
.eo-bar { display: flex; align-items: center; gap: 12px; }
.eo-name { font-weight: 600; font-size: 14px; color: #1d2939; flex: 1; }
.eo-code { font-style: normal; font-size: 11px; color: #98a2b3; margin-left: 8px; font-family: Consolas, monospace; }
.eo-canvas {
  width: 100%; border-radius: 8px; cursor: crosshair;
  border: 1px solid #344053; background: #0a120d;
}
.eo-ops { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.eo-tgt { display: flex; gap: 10px; align-items: center; font-size: 12px; color: #475467;
  background: #f8fafc; border: 1px solid #eaecf0; padding: 4px 10px; border-radius: 8px; }
.eo-hint { font-size: 12px; color: #98a2b3; }
.eo-hint.right { margin-left: auto; }
</style>
