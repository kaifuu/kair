<template>
  <div class="vp">
    <div class="vp-list">
      <div v-if="!devices.length" class="vp-empty">暂无视频监控设备</div>
      <div v-for="d in devices" :key="d.id" class="vp-card">

        <!-- 真实直播流:HLS(m3u8) 经服务端 /api/video/proxy 代理转发,跨域由代理解决 -->
        <div v-if="d.videoUrl && !failed[d.id]" class="vp-view vp-live-view">
          <video :ref="(el) => (videoEls[d.id] = el)" class="vp-video"
                 muted autoplay playsinline controls preload="auto"
                 @error="onVideoError(d)"></video>
          <span class="vp-live"><i></i>LIVE</span>
          <span v-if="buffering[d.id]" class="vp-buf">信号接入中…</span>
        </div>

        <!-- 模拟快照回落:未配置 videoUrl 或直播流不可用时,按设备编码生成确定性场景 -->
        <div v-else class="vp-view" @click="$emit('open', d)">
          <svg :viewBox="`0 0 160 78`" preserveAspectRatio="none">
            <defs>
              <linearGradient :id="`sky-${d.id}`" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0" stop-color="#a8d4f5" />
                <stop offset="1" stop-color="#e6f3fd" />
              </linearGradient>
            </defs>
            <rect width="160" height="78" :fill="`url(#sky-${d.id})`" />
            <circle :cx="sunX(d.id)" cy="14" r="6.5" fill="#ffd66b" opacity="0.9" />
            <g opacity="0.85">
              <rect v-for="(b, i) in bars(d.id)" :key="i"
                    :x="4 + i * 15" :y="64 - b" width="11" :height="b"
                    :fill="i % 2 ? '#7d9ec4' : '#8fb2d6'" rx="0.5" />
            </g>
            <rect y="64" width="160" height="14" fill="#93ac7e" />
            <rect y="64" width="160" height="2" fill="#6f8a5c" opacity="0.7" />
            <ellipse cx="40" cy="16" rx="14" ry="4.5" fill="#fff" opacity="0.75">
              <animate attributeName="cx" values="20;150;20" dur="36s" repeatCount="indefinite" />
            </ellipse>
            <rect y="0" width="160" height="1.5" fill="#36cfc9" opacity="0.85">
              <animate attributeName="y" values="0;76;0" dur="5s" repeatCount="indefinite" />
            </rect>
            <text x="4" y="74" font-size="6.5" fill="#17324d" font-family="Consolas,monospace">
              {{ d.code }} · {{ clock }}
            </text>
          </svg>
          <span class="vp-rec"><i></i>REC</span>
          <span v-if="alarmsOf(d) > 0" class="vp-alarm">AI 告警 ×{{ alarmsOf(d) }}</span>
        </div>

        <div class="vp-meta" @click="$emit('open', d)">
          <span class="vp-name" :title="d.name">{{ d.name }}</span>
          <span class="vp-kbps">
            {{ fieldsOf(d) ? fmtField('bitrateKbps', fieldsOf(d).bitrateKbps) : '—' }} ·
            {{ fieldsOf(d) ? fmtField('fps', fieldsOf(d).fps) : '—' }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import Hls from 'hls.js'
import { fmtField } from '../utils/deviceFields'

const props = defineProps({
  devices: { type: Array, default: () => [] },   // CAMERA 设备
  latest: { type: Object, default: () => ({}) }
})
defineEmits(['open'])

const fieldsOf = (d) => props.latest[d.id]?.fields
const alarmsOf = (d) => Number(fieldsOf(d)?.alarms || 0)

/* ---------- 直播流播放(hls.js,瞬时抖动自动重试,多次失败回落模拟快照) ---------- */
const videoEls = reactive({})
const failed = reactive({})      // deviceId -> true 表示该路流不可用
const buffering = reactive({})   // deviceId -> true 表示缓冲中
const retries = reactive({})     // deviceId -> 已重试次数
const MAX_RETRY = 3
let auth401 = false              // 登录过期已提示(多路视频只弹一次)
const hlsMap = new Map()

watch(() => props.devices, async (list) => {
  await nextTick()
  for (const d of list || []) attachStream(d)
}, { immediate: true })

function proxyUrl(d) {
  const token = localStorage.getItem('token') || ''
  return `/api/video/proxy?url=${encodeURIComponent(d.videoUrl)}` +
    (token ? `&token=${encodeURIComponent(token)}` : '')
}

function attachStream(d) {
  const el = videoEls[d.id]
  if (!el || !d.videoUrl || failed[d.id] || hlsMap.has(d.id)) return
  buffering[d.id] = true
  el.addEventListener('playing', () => { buffering[d.id] = false })
  const src = proxyUrl(d)
  // Safari 原生 HLS(video 标签无法带 Authorization,走 url token 参数)
  if (el.canPlayType('application/vnd.apple.mpegurl')) {
    el.src = src
    return
  }
  if (!Hls.isSupported()) {
    failed[d.id] = true
    return
  }
  const hls = new Hls({
    xhrSetup: (xhr) => {
      const token = localStorage.getItem('token')
      if (token) xhr.setRequestHeader('Authorization', 'Bearer ' + token)
    }
  })
  hlsMap.set(d.id, hls)
  hls.loadSource(src)
  hls.attachMedia(el)
  hls.on(Hls.Events.ERROR, (e, data) => {
    // 401 不会自愈,不必等 fatal/hls 内部重试,立刻引导重新登录
    // (监控页走 WebSocket 无 axios 轮询,登录过期不会自动跳登录——这里补上与全局一致的引导)
    if (data.response?.code === 401) {
      destroyHls(d.id)
      if (!auth401) {
        auth401 = true
        ElMessage.error('登录已过期,请重新登录')
        localStorage.removeItem('token')
        setTimeout(() => { location.hash = '#/login' }, 800)
      }
      return
    }
    if (!data.fatal) return
    destroyHls(d.id)
    // 公网直播源偶发超时/断流:先重试几次,仍失败才回落模拟快照
    retries[d.id] = (retries[d.id] || 0) + 1
    if (retries[d.id] <= MAX_RETRY) {
      setTimeout(() => attachStream(d), 2500)
    } else {
      failed[d.id] = true
    }
  })
}

function onVideoError(d) {
  if (d.videoUrl) failed[d.id] = true
}

function destroyHls(id) {
  hlsMap.get(id)?.destroy()
  hlsMap.delete(id)
}

onUnmounted(() => {
  for (const id of [...hlsMap.keys()]) destroyHls(id)
})

/** 时间戳水印(秒级跳动) */
const clock = ref('')
let timer = null
onMounted(() => {
  const tick = () => { clock.value = new Date().toLocaleTimeString('zh-CN', { hour12: false }) }
  tick()
  timer = setInterval(tick, 1000)
})
onUnmounted(() => clearInterval(timer))

/** 设备 id 伪随机天际线(确定性,同一设备每次相同) */
function bars(id) {
  let h = (id * 2654435761) % 10007 || 7
  const out = []
  for (let i = 0; i < 10; i++) {
    h = (h * 1103515245 + 12345) % 2147483648
    out.push(14 + (h % 32))
  }
  return out
}

const sunX = (id) => 100 + ((id * 37) % 48)
</script>

<style scoped>
.vp { display: flex; flex-direction: column; min-height: 0; flex: 1; }
.vp-list { flex: 1; overflow-y: auto; padding: 2px 12px 12px; max-height: 300px; }
.vp-empty { padding: 22px 0; text-align: center; color: var(--text-faint); font-size: 13px; }

.vp-card {
  margin-bottom: 10px;
  border: 1px solid var(--border); border-radius: 10px; overflow: hidden;
  background: #fff; transition: all .2s;
}
.vp-card:hover { border-color: #b8ccf7; box-shadow: var(--shadow-sm); }

.vp-view { position: relative; height: 96px; background: #0b1c2c; }
.vp-view svg { width: 100%; height: 100%; display: block; }
.vp-live-view { cursor: default; }
.vp-video {
  width: 100%; height: 100%;
  object-fit: cover; display: block;
  background: #000;
}
.vp-live {
  position: absolute; top: 6px; left: 6px;
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 10px; font-weight: 700; color: #fff; letter-spacing: 1px;
  background: rgba(240, 68, 56, 0.85); padding: 1px 6px; border-radius: 4px;
}
.vp-live i { width: 6px; height: 6px; border-radius: 50%; background: #fff; animation: vp-blink 1.2s infinite; }
.vp-buf {
  position: absolute; right: 6px; bottom: 6px;
  font-size: 10px; color: #fff;
  background: rgba(11, 28, 44, 0.65); padding: 1px 7px; border-radius: 4px;
  animation: vp-blink 1.4s infinite;
}
.vp-rec {
  position: absolute; top: 6px; left: 6px;
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 10px; font-weight: 700; color: #fff; letter-spacing: 1px;
  background: rgba(11, 28, 44, 0.55); padding: 1px 6px; border-radius: 4px;
}
.vp-rec i { width: 6px; height: 6px; border-radius: 50%; background: #f04438; animation: vp-blink 1.2s infinite; }
@keyframes vp-blink { 50% { opacity: 0.2; } }
.vp-alarm {
  position: absolute; top: 6px; right: 6px;
  font-size: 10px; font-weight: 700; color: #fff;
  background: rgba(240, 68, 56, 0.85); padding: 1px 7px; border-radius: 4px;
}

.vp-meta {
  display: flex; justify-content: space-between; align-items: center; gap: 8px;
  padding: 7px 10px;
  cursor: pointer;
}
.vp-name { font-size: 12.5px; font-weight: 600; color: #101828; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.vp-kbps { font-size: 11px; color: var(--text-dim); flex-shrink: 0; font-family: Consolas, monospace; }
</style>
