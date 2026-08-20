<template>
  <div class="dv">
    <!-- 真实回传流:设备配置了 videoUrl(HLS)时经 /api/video/proxy 代理播放 -->
    <div v-if="hasUrl && !fallback" class="dv-view">
      <video ref="videoEl" class="dv-video" muted autoplay playsinline controls preload="auto"
             @error="onVideoError"></video>
      <span class="dv-live"><i></i>LIVE</span>
      <span v-if="buffering" class="dv-buf">图传接入中…</span>
    </div>

    <!-- 模拟图传:按实时遥测(航向/速度/高度)驱动的第一视角画面 + FPV HUD -->
    <div v-else class="dv-view">
      <canvas ref="canvasEl" class="dv-canvas"></canvas>
      <span class="dv-sim">{{ hasUrl ? '信号中断 · 模拟画面' : '模拟图传(配置视频流地址后回传真实画面)' }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import Hls from 'hls.js'

const props = defineProps({
  /** 在飞遥测(reactive,live 更新):lng/lat/altitude/speed/heading/battery/satellites/droneCode */
  telemetry: { type: Object, default: null },
  /** 无人机设备档案:videoUrl 配置了真实 HLS 流则播放之 */
  device: { type: Object, default: null }
})

const hasUrl = computed(() => !!props.device?.videoUrl)

/* ---------- 真实流播放(hls.js,瞬时抖动重试,多次失败回落模拟画面) ---------- */
const videoEl = ref(null)
const buffering = ref(false)
const fallback = ref(false)
let retries = 0
let auth401 = false
let hls = null

function attachStream() {
  const el = videoEl.value
  if (!el || !hasUrl.value) return
  buffering.value = true
  el.addEventListener('playing', () => { buffering.value = false }, { once: true })
  const token = localStorage.getItem('token') || ''
  const src = `/api/video/proxy?url=${encodeURIComponent(props.device.videoUrl)}` +
    (token ? `&token=${encodeURIComponent(token)}` : '')
  // Safari 原生 HLS(video 标签无法带 Authorization,走 url token 参数)
  if (el.canPlayType('application/vnd.apple.mpegurl')) { el.src = src; return }
  if (!Hls.isSupported()) { fallback.value = true; return }
  hls = new Hls({
    xhrSetup: (xhr) => {
      const t = localStorage.getItem('token')
      if (t) xhr.setRequestHeader('Authorization', 'Bearer ' + t)
    }
  })
  hls.loadSource(src)
  hls.attachMedia(el)
  hls.on(Hls.Events.ERROR, (e, data) => {
    if (data.response?.code === 401) {
      destroyHls()
      if (!auth401) {
        auth401 = true
        ElMessage.error('登录已过期,请重新登录')
        localStorage.removeItem('token')
        setTimeout(() => { location.hash = '#/login' }, 800)
      }
      return
    }
    if (!data.fatal) return
    destroyHls()
    if (++retries <= 3) setTimeout(attachStream, 2500)
    else fallback.value = true
  })
}
function onVideoError() { if (hasUrl.value) fallback.value = true }
function destroyHls() { hls?.destroy(); hls = null }

watch(() => props.device?.id, async () => {
  fallback.value = false
  retries = 0
  destroyHls()
  await nextTick()
  attachStream()
}, { immediate: true })
onUnmounted(destroyHls)

/* ---------- 模拟 FPV:canvas 第一视角 + HUD(遥测插值平滑) ---------- */
const canvasEl = ref(null)
const eased = reactive({ heading: 0, speed: 0, altitude: 0, battery: 100, satellites: 0 })
let raf = 0, lastT = 0, scroll = 0, bank = 0, bankT = 0, ro = null

/** 设备 id 伪随机(地面景物种子,同一机每次画面一致) */
const seedOf = (id) => ((id || 1) * 2654435761) % 4294967296
const rnd = (s) => { s = (s * 1103515245 + 12345) % 2147483648; return s / 2147483648 }

function frame(t) {
  raf = requestAnimationFrame(frame)
  const cv = canvasEl.value
  if (!cv) return
  const dt = Math.min(0.05, (t - lastT) / 1000 || 0.016)
  lastT = t

  // 遥测 → 视觉量(缓动;航向按最短角差)
  const tm = props.telemetry || {}
  const targetH = tm.heading ?? eased.heading
  let dh = ((targetH - eased.heading + 540) % 360) - 180
  eased.heading = (eased.heading + dh * Math.min(1, dt * 2) + 360) % 360
  eased.speed += ((tm.speed ?? 0) - eased.speed) * Math.min(1, dt * 2)
  eased.altitude += ((tm.altitude ?? 0) - eased.altitude) * Math.min(1, dt * 2)
  eased.battery += ((tm.battery ?? 100) - eased.battery) * Math.min(1, dt)
  eased.satellites = tm.satellites ?? eased.satellites

  scroll += Math.max(0.4, eased.speed) * dt * 40        // 地面滚动(慢速也保持微动)
  bankT += dt
  bank = Math.sin(bankT * 0.6) * 3.5                    // 缓慢侧倾摆动

  const ctx = cv.getContext('2d')
  const w = cv.width, h = cv.height
  const dpr = window.devicePixelRatio || 1
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  const W = w / dpr, H = h / dpr

  ctx.save()
  ctx.clearRect(0, 0, W, H)
  ctx.translate(W / 2, H / 2)
  ctx.rotate(bank * Math.PI / 180)
  ctx.translate(-W / 2, -H / 2)

  const hz = H * 0.42 + Math.sin(bankT * 0.9) * 3       // 地平线(微俯仰)

  // 天空
  const sky = ctx.createLinearGradient(0, 0, 0, hz)
  sky.addColorStop(0, '#8fc4ee'); sky.addColorStop(1, '#d9ecfb')
  ctx.fillStyle = sky
  ctx.fillRect(-W * 0.2, -H * 0.2, W * 1.4, hz + H * 0.2)

  // 地面
  const gnd = ctx.createLinearGradient(0, hz, 0, H * 1.2)
  gnd.addColorStop(0, '#a8bd8f'); gnd.addColorStop(1, '#6f8a5c')
  ctx.fillStyle = gnd
  ctx.fillRect(-W * 0.2, hz, W * 1.4, H * 1.2)

  // 透视网格:纵向线向消失点收敛,横向线按滚动相位推进
  ctx.strokeStyle = 'rgba(255,255,255,0.28)'
  ctx.lineWidth = 1
  const vpx = W / 2
  for (let i = -8; i <= 8; i++) {
    ctx.beginPath()
    ctx.moveTo(vpx + i * 14, hz)
    ctx.lineTo(vpx + i * W * 0.16, H * 1.2)
    ctx.stroke()
  }
  for (let i = 0; i < 9; i++) {
    const ph = ((i * 90 + scroll) % 810)
    const y = hz + (H - hz) * (ph / 810) ** 2.4          // 越近越疏(透视)
    if (y <= H * 1.15) {
      ctx.globalAlpha = 0.15 + 0.5 * (y - hz) / (H - hz)
      ctx.beginPath(); ctx.moveTo(-W * 0.2, y); ctx.lineTo(W * 1.2, y); ctx.stroke()
    }
  }
  ctx.globalAlpha = 1

  // 地面景物(伪随机方块建筑/树,两列随滚动下移)
  let s = seedOf(props.device?.id)
  for (let i = 0; i < 26; i++) {
    const lane = rnd(s), off = rnd(s), ht = 4 + rnd(s) * 16, isTree = rnd(s) > 0.55
    const ph = ((off * 810 + scroll) % 810)
    const y = hz + (H - hz) * (ph / 810) ** 2.4
    if (y > H * 1.1) continue
    const scale = (y - hz) / (H - hz)
    const x = vpx + (lane - 0.5) * W * 1.5 * (0.25 + scale)
    const bw = (isTree ? 3 : 6) * (0.3 + scale * 2.2)
    const bh = ht * (0.25 + scale * 2.2)
    ctx.fillStyle = isTree ? '#4f7a3e' : ['#b9c4d6', '#a5b3c9', '#c7d2e2'][i % 3]
    ctx.fillRect(x, y - bh, bw, bh)
  }

  // 远景薄雾
  const haze = ctx.createLinearGradient(0, hz - 10, 0, hz + 26)
  haze.addColorStop(0, 'rgba(233,243,252,0)'); haze.addColorStop(0.5, 'rgba(233,243,252,0.55)'); haze.addColorStop(1, 'rgba(233,243,252,0)')
  ctx.fillStyle = haze
  ctx.fillRect(-W * 0.2, hz - 10, W * 1.4, 36)
  ctx.restore()

  drawHud(ctx, W, H)
}

function drawHud(ctx, W, H) {
  const cyan = '#8ef0ff'
  ctx.font = '600 10px Consolas, monospace'
  ctx.strokeStyle = cyan
  ctx.fillStyle = cyan
  ctx.lineWidth = 1.2

  // 中心十字与速度矢量
  const cx = W / 2, cy = H * 0.5
  ctx.beginPath()
  ctx.moveTo(cx - 26, cy); ctx.lineTo(cx - 9, cy); ctx.moveTo(cx + 9, cy); ctx.lineTo(cx + 26, cy)
  ctx.moveTo(cx, cy - 20); ctx.lineTo(cx, cy - 7)
  ctx.stroke()
  ctx.beginPath(); ctx.arc(cx, cy, 2.2, 0, 7); ctx.stroke()

  // 航向带(顶部刻度 + 读数)
  const tapeY = 12
  for (let d = -60; d <= 60; d += 15) {
    const deg = (eased.heading + d + 360) % 360
    const x = cx + d * (W / 170)
    const major = Math.round(deg) % 45 === 0
    ctx.beginPath(); ctx.moveTo(x, tapeY); ctx.lineTo(x, tapeY + (major ? 6 : 3)); ctx.stroke()
    if (major) {
      const lbl = { 0: 'N', 90: 'E', 180: 'S', 270: 'W' }[Math.round(deg) % 360] ?? String(Math.round(deg / 10) * 10 % 360)
      ctx.fillText(lbl, x - ctx.measureText(lbl).width / 2, tapeY - 2)
    }
  }
  ctx.beginPath(); ctx.moveTo(cx, tapeY + 9); ctx.lineTo(cx - 4, tapeY + 14); ctx.lineTo(cx + 4, tapeY + 14); ctx.closePath(); ctx.fill()
  const hTxt = String(Math.round(eased.heading)).padStart(3, '0') + '°'
  ctx.font = '700 13px Consolas, monospace'
  ctx.strokeText(hTxt, cx - 18, tapeY + 27); ctx.fillText(hTxt, cx - 18, tapeY + 27)

  // 左:高度 / 右:地速(带框读数)
  ctx.font = '600 11px Consolas, monospace'
  box(ctx, 8, H * 0.5 - 10, 'ALT', eased.altitude.toFixed(0) + 'm')
  box(ctx, W - 78, H * 0.5 - 10, 'SPD', eased.speed.toFixed(1) + 'm/s')
  box(ctx, 8, H * 0.5 + 16, 'BAT', Math.round(eased.battery) + '%')
  box(ctx, W - 78, H * 0.5 + 16, 'SAT', String(eased.satellites))

  // 状态角标:REC + 机号 + 时钟
  ctx.fillStyle = '#ff5b4d'
  ctx.beginPath(); ctx.arc(14, H - 14, 3, 0, 7); ctx.fill()
  ctx.fillStyle = '#fff'
  ctx.fillText('REC ' + new Date().toLocaleTimeString('zh-CN', { hour12: false }), 22, H - 10)
  const code = props.telemetry?.droneCode || props.device?.code || ''
  if (code) ctx.fillText(code, W - ctx.measureText(code).width - 8, H - 10)
}
function box(ctx, x, y, label, val) {
  ctx.strokeStyle = 'rgba(142,240,255,0.85)'
  ctx.strokeRect(x, y, 70, 18)
  ctx.fillStyle = 'rgba(9,30,46,0.55)'
  ctx.fillRect(x, y, 70, 18)
  ctx.fillStyle = '#8ef0ff'
  ctx.font = '600 9px Consolas, monospace'
  ctx.fillText(label, x + 4, y + 8)
  ctx.fillStyle = '#fff'
  ctx.font = '700 11px Consolas, monospace'
  ctx.fillText(val, x + 26, y + 13.5)
}

function fitCanvas() {
  const cv = canvasEl.value
  if (!cv) return
  const dpr = window.devicePixelRatio || 1
  const r = cv.getBoundingClientRect()
  cv.width = Math.max(2, Math.round(r.width * dpr))
  cv.height = Math.max(2, Math.round(r.height * dpr))
}

onMounted(() => {
  fitCanvas()
  // 弹窗展开有过渡,canvas 首次布局可能 0×0 —— 用 ResizeObserver 跟踪容器实际尺寸
  ro = new ResizeObserver(fitCanvas)
  ro.observe(canvasEl.value)
  window.addEventListener('resize', fitCanvas)
  raf = requestAnimationFrame(frame)
})
onUnmounted(() => {
  cancelAnimationFrame(raf)
  ro?.disconnect()
  window.removeEventListener('resize', fitCanvas)
})
</script>

<style scoped>
.dv { width: 100%; }
.dv-view {
  position: relative; width: 100%; aspect-ratio: 16 / 9;
  border-radius: 10px; overflow: hidden; background: #0b1c2c;
  border: 1px solid rgba(142, 240, 255, 0.35);
}
.dv-video { width: 100%; height: 100%; object-fit: cover; display: block; background: #000; }
.dv-canvas { width: 100%; height: 100%; display: block; }
.dv-live {
  position: absolute; top: 6px; left: 6px;
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 10px; font-weight: 700; color: #fff; letter-spacing: 1px;
  background: rgba(240, 68, 56, 0.85); padding: 1px 6px; border-radius: 4px;
}
.dv-live i { width: 6px; height: 6px; border-radius: 50%; background: #fff; animation: dv-blink 1.2s infinite; }
.dv-buf {
  position: absolute; right: 6px; bottom: 6px; font-size: 10px; color: #fff;
  background: rgba(11, 28, 44, 0.65); padding: 1px 7px; border-radius: 4px;
  animation: dv-blink 1.4s infinite;
}
.dv-sim {
  position: absolute; right: 6px; top: 6px; font-size: 10px; color: #cfe9ff;
  background: rgba(11, 28, 44, 0.6); padding: 1px 7px; border-radius: 4px;
  border: 1px solid rgba(142, 240, 255, 0.3);
}
@keyframes dv-blink { 50% { opacity: 0.2; } }
</style>
