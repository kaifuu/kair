<template>
  <div class="drill-page">
    <!-- ============ 左栏:反制装备库 / 布防清单 ============ -->
    <aside class="side left" :class="{ collapsed: sideCollapsed }">
      <div class="side-head">
        <span v-if="!sideCollapsed">反制装备库</span>
        <el-button :icon="sideCollapsed ? Expand : Fold" text size="small" @click="sideCollapsed = !sideCollapsed" />
      </div>
      <template v-if="!sideCollapsed">
        <div class="lib-tip">拖拽装备到地图布防,或点选后点击地图放置</div>
        <div class="lib-list">
          <div
            v-for="d in counterDevices"
            :key="d.id"
            class="lib-item"
            :class="{ placing: placingId === d.id, placed: isPlaced(d.id) }"
            draggable="true"
            @dragstart="onDragStart($event, d)"
            @click="togglePlacing(d)"
          >
            <img :src="deviceSvg(d.category)" class="lib-icon" alt="" />
            <div class="lib-info">
              <b>{{ d.name }}</b>
              <span>{{ COUNTER_META[d.category]?.label }} · 作用 {{ rangeOf(d) }}m</span>
            </div>
            <el-tag v-if="isPlaced(d.id)" size="small" type="success" effect="plain">已布防</el-tag>
          </div>
          <div v-if="!counterDevices.length" class="lib-empty">
            暂无反制设备,请到「设备管理」添加警戒雷达/无线电探测/光电跟踪/无线电压制/激光处置/网捕无人机
          </div>
        </div>

        <div class="side-head sec">
          <span>已布防 {{ placedList.length }} 处</span>
          <span class="head-ops">
            <el-button size="small" text type="primary" @click="autoPlace">一键布防</el-button>
            <el-button size="small" text @click="clearPlacements" :disabled="phase !== 'IDLE' || !placedList.length">清空</el-button>
          </span>
        </div>
        <div class="placed-list">
          <div v-for="p in placedList" :key="p.deviceId" class="placed-item">
            <i class="dot" :style="{ background: deviceMeta(p.category).color }" />
            <div class="placed-info">
              <b>{{ p.name }}</b>
              <span>{{ COUNTER_META[p.category]?.label }} · {{ p.lng.toFixed(4) }},{{ p.lat.toFixed(4) }} · {{ Math.round(p.scanRange) }}m</span>
            </div>
            <el-button
              v-if="p.category === 'EO_TRACK'"
              size="small" text type="primary" @click="openEo(p)"
            >视窗</el-button>
            <el-button
              v-if="phase === 'IDLE'"
              size="small" text type="danger" @click="removePlacement(p.deviceId)"
            >移除</el-button>
          </div>
          <div v-if="!placedList.length" class="lib-empty">尚未布防</div>
        </div>
      </template>
    </aside>

    <!-- ============ 中央地图 ============ -->
    <main class="map-area">
      <!-- 顶部演练控制条 -->
      <div class="drill-toolbar">
        <template v-if="phase === 'IDLE'">
          <span class="tb-label">敌机数量</span>
          <el-input-number v-model="enemyCount" :min="1" :max="8" size="small" style="width: 90px" />
          <span class="tb-label">AI 自动守候</span>
          <el-switch v-model="autoguardOn" size="small" />
          <el-button type="primary" size="small" :disabled="!placedList.length" :loading="busy" @click="startDrill">
            <el-icon><VideoPlay /></el-icon>&nbsp;开始演练
          </el-button>
        </template>
        <template v-else-if="phase === 'RUNNING'">
          <span class="tb-label">AI 自动守候</span>
          <el-switch :model-value="state.autoguard" size="small" @change="(v) => setAutoguard(v)" />
          <span class="tb-label">速度</span>
          <el-radio-group :model-value="state.speed" size="small" @change="(v) => setSpeed(v)">
            <el-radio-button :value="1">1×</el-radio-button>
            <el-radio-button :value="2">2×</el-radio-button>
            <el-radio-button :value="4">4×</el-radio-button>
          </el-radio-group>
          <el-input-number v-model="waveCount" :min="1" :max="8" size="small" style="width: 84px" />
          <el-select v-model="waveKind" size="small" style="width: 104px">
            <el-option label="随机机型" value="" />
            <el-option label="侦察巡飞" value="SCOUT" />
            <el-option label="快速穿越" value="FAST" />
          </el-select>
          <el-button size="small" @click="sendWave" :loading="busy">
            <el-icon><Plus /></el-icon>&nbsp;增派敌机
          </el-button>
          <el-button type="danger" size="small" plain @click="stopDrill" :loading="busy">中止演练</el-button>
        </template>
        <template v-else>
          <el-tag type="success" effect="dark">演练结束 · 评分 {{ state.stats?.score ?? '-' }}</el-tag>
          <el-button type="primary" size="small" @click="resetDrill">重新布防</el-button>
        </template>

        <div class="tb-stats" v-if="phase !== 'IDLE' && state.stats">
          <span>投放 <b>{{ state.stats.total }}</b></span>
          <span>在飞 <b class="c-red">{{ state.stats.flying }}</b></span>
          <span>探测 <b>{{ state.stats.detected }}</b></span>
          <span>处置 <b class="c-green">{{ state.stats.neutralized }}</b></span>
          <span>逃脱 <b class="c-orange">{{ state.stats.escaped }}</b></span>
          <span>响应 <b>{{ fmtResp(state.stats.avgResponseMs) }}</b></span>
          <span>耗时 <b>{{ fmtElapsed(state.stats.elapsedMs) }}</b></span>
        </div>
      </div>

      <div
        class="map-box"
        @dragover.prevent
        @drop="onDrop"
      >
        <div ref="mapRef" class="map-container" />
        <div v-if="mapLoading" class="map-loading" v-loading="true" element-loading-text="地图加载中..." />

        <!-- 图例 -->
        <div class="legend">
          <span><i class="dot" style="background:#d92d20" />入侵敌机</span>
          <span><i class="dot" style="background:#f79009" />压制失控</span>
          <span><i class="dot" style="background:#7a5af8" />网捕中</span>
          <span><i class="dot" style="background:#98a2b3" />已终结</span>
          <span><i class="ring" />核心防护区</span>
        </div>

        <!-- 选中敌机处置卡 -->
        <div v-if="selEnemy && phase === 'RUNNING'" class="enemy-card">
          <div class="ec-head">
            <b>{{ selEnemy.id }}</b>
            <el-tag size="small" :type="enemyTagType(selEnemy.status)" effect="dark">{{ STATUS_TEXT[selEnemy.status] }}</el-tag>
            <span class="ec-kind">{{ selEnemy.kind === 'FAST' ? '快速穿越机' : '侦察巡飞机' }}</span>
            <el-button class="ec-close" text size="small" :icon="Close" @click="selEnemyId = null" />
          </div>
          <div class="ec-body">
            <span>高度 <b>{{ selEnemy.alt }}m</b></span>
            <span>速度 <b>{{ selEnemy.speed }}m/s</b></span>
            <span>航向 <b>{{ selEnemy.heading }}°</b></span>
            <span>跟踪 <b :class="selEnemy.tracked ? 'c-green' : 'c-orange'">{{ selEnemy.tracked ? '已锁定' : '未锁定' }}</b></span>
            <span>闯入 <b :class="selEnemy.intruded ? 'c-red' : ''">{{ selEnemy.intruded ? '已闯入' : '未闯入' }}</b></span>
          </div>
          <div class="ec-ops">
            <el-button
              v-for="c in capableCounters"
              :key="c.deviceId"
              size="small"
              :type="c.action === 'DESTROY' ? 'danger' : 'warning'"
              :disabled="busy"
              @click="engage(c.deviceId, selEnemy.id)"
            >{{ c.name }} · {{ ACTION_TEXT[c.action] }}</el-button>
            <span v-if="!capableCounters.length" class="ec-none">暂无可用反制装备(不在作用范围/冷却中)</span>
          </div>
        </div>
      </div>
    </main>

    <!-- ============ 右栏:事件流 / 演练记录 ============ -->
    <aside class="side right">
      <div class="side-head"><span>演练事件</span>
        <el-tag v-if="state.autoguard && phase === 'RUNNING'" size="small" type="success" effect="plain">AI 守候中</el-tag>
      </div>
      <div class="event-feed" ref="feedRef">
        <div v-if="!events.length" class="lib-empty" style="padding:24px 0">暂无事件</div>
        <div v-for="ev in events" :key="ev.id" class="event-item" :class="'lv-' + (ev.level || 'INFO').toLowerCase()">
          <i class="ev-dot" />
          <div class="ev-body">
            <div class="ev-text">{{ ev.text }}</div>
            <div class="ev-time">{{ fmtEvTime(ev.ts) }}</div>
          </div>
        </div>
      </div>

      <div class="side-head sec"><span>演练记录</span></div>
      <div class="runs-list">
        <div v-for="r in runs" :key="r.id" class="run-item" @click="showRun(r)">
          <div class="run-line1">
            <b>#{{ r.id }}</b>
            <el-tag size="small" :type="r.status === 'COMPLETED' ? 'success' : 'info'" effect="plain">
              {{ r.status === 'COMPLETED' ? '完成' : '中止' }}
            </el-tag>
            <el-tag v-if="r.autoguard" size="small" type="success" effect="plain">AI</el-tag>
            <b class="run-score">{{ r.score }}分</b>
          </div>
          <div class="run-line2">
            {{ fmtRunTime(r.startedAt) }} · 处置 {{ r.neutralized }}/{{ r.enemiesTotal }} · 逃脱 {{ r.escaped }}
          </div>
        </div>
        <div v-if="!runs.length" class="lib-empty" style="padding:16px 0">暂无演练记录</div>
      </div>
    </aside>

    <!-- ============ 光电视窗 ============ -->
    <EoWindow
      v-model="eoWin.open"
      :eo="eoWin.placement"
      :enemies="state.enemies || []"
      :counters="counterPlacements"
      @engage="onEoEngage"
    />

    <!-- ============ 演练结束汇总 ============ -->
    <el-dialog v-model="endDlg.open" title="演练结束 · 战果汇总" width="560px" append-to-body>
      <div class="end-score">
        <div class="score-num" :class="scoreClass">{{ endDlg.data?.stats?.score ?? 0 }}</div>
        <div class="score-label">综合评分</div>
      </div>
      <div class="end-stats">
        <div class="es-item"><b>{{ endDlg.data?.stats?.total ?? 0 }}</b><span>投放敌机</span></div>
        <div class="es-item"><b>{{ endDlg.data?.stats?.detected ?? 0 }}</b><span>探测发现</span></div>
        <div class="es-item c-green"><b>{{ endDlg.data?.stats?.neutralized ?? 0 }}</b><span>处置成功</span></div>
        <div class="es-item c-orange"><b>{{ endDlg.data?.stats?.escaped ?? 0 }}</b><span>逃脱</span></div>
        <div class="es-item"><b>{{ fmtResp(endDlg.data?.stats?.avgResponseMs) }}</b><span>平均响应</span></div>
      </div>
      <el-table :data="endDlg.data?.enemies || []" size="small" style="margin-top:16px">
        <el-table-column prop="id" label="敌机" width="70" />
        <el-table-column label="机型" width="90">
          <template #default="{ row }">{{ row.kind === 'FAST' ? '快速穿越' : '侦察巡飞' }}</template>
        </el-table-column>
        <el-table-column label="处置结果">
          <template #default="{ row }">
            <el-tag size="small" :type="row.outcome === '未处置' ? 'danger' : 'success'" effect="plain">
              {{ row.outcome || '未处置' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="闯入核心区" width="100">
          <template #default="{ row }">{{ row.intruded ? '是' : '否' }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="endDlg.open = false">关闭</el-button>
        <el-button type="primary" @click="endDlg.open = false; resetDrill()">重新布防</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 攻防演练页:
 * - 布防阶段:从装备库拖拽/点选反制设备上地图,布防探测+反制装备,圈定作用范围
 * - 演练阶段:投放敌机逼近核心防护区,进入探测范围产生演练告警;人工或 AI 自动守候处置
 * - 结束:战果汇总落库,可回看演练记录
 * 数据源:REST(/drill/*)+ WS(type=drill,每秒全量快照);坐标 BD-09。
 */
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import http from '../api'
import { createMap, distMeters } from '../utils/mapAdapter'
import { deviceSvg, deviceMeta, COUNTER_META } from '../utils/map'
import { VideoPlay, Plus, Close, Fold, Expand } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import EoWindow from '../components/EoWindow.vue'

const STATUS_TEXT = {
  FLYING: '逼近中', JAMMED: '压制失控', CAPTURING: '网捕拦截中',
  NEUTRALIZED: '已处置', ESCAPED: '已撤离'
}
const ENEMY_COLOR = {
  FLYING: '#d92d20', JAMMED: '#f79009', CAPTURING: '#7a5af8',
  NEUTRALIZED: '#667085', ESCAPED: '#98a2b3'
}
const ACTION_TEXT = { JAM: '压制驱离', DESTROY: '激光击落', CAPTURE: '网捕捕获' }

/* ---------- 基础状态 ---------- */
const mapRef = ref(null)
const mapLoading = ref(true)
let mapApi = null

const devices = ref([])
const counterDevices = computed(() => devices.value.filter((d) => COUNTER_META[d.category]))
const rangeOf = (d) => d.scanRange || COUNTER_META[d.category]?.defaultRange || 1000

const state = ref({ phase: 'IDLE' })          // 后端演练快照
const phase = computed(() => state.value.phase || 'IDLE')
const pre = ref([])                            // 布防阶段 placements(前端持有)
const placedList = computed(() =>
  phase.value === 'IDLE' ? pre.value : (state.value.placements || []))

const enemyCount = ref(3)
const autoguardOn = ref(true)
const waveCount = ref(2)
const waveKind = ref('')
const busy = ref(false)
const sideCollapsed = ref(false)
const placingId = ref(null)                    // 点选放置模式(deviceId)
const selEnemyId = ref(null)
const runs = ref([])
const feedRef = ref(null)
const eoWin = reactive({ open: false, placement: null })
const endDlg = reactive({ open: false, data: null })

const events = computed(() => [...(state.value.events || [])].reverse())
const selEnemy = computed(() =>
  (state.value.enemies || []).find((e) => e.id === selEnemyId.value) || null)
const counterPlacements = computed(() =>
  (state.value.placements || []).filter((p) => p.role === 'counter'))
const scoreClass = computed(() => {
  const s = endDlg.data?.stats?.score ?? 0
  return s >= 80 ? 'c-green' : s >= 60 ? 'c-orange' : 'c-red'
})

/** 选中敌机的可用反制装备(范围内 + 非冷却;激光需光电锁定) */
const capableCounters = computed(() => {
  const e = selEnemy.value
  if (!e) return []
  return counterPlacements.value.filter((c) => {
    if (c.cooling) return false
    if (distMeters(c, e) > c.scanRange) return false
    if (c.action === 'DESTROY' && !e.tracked) return false
    return true
  })
})

/* ---------- 地图覆盖物 ---------- */
const overlays = {
  placed: new Map(),      // deviceId -> {marker,label,range}
  enemies: new Map(),     // enemyId -> {marker,label,track}
  core: []
}
const CORE = { lng: 116.397, lat: 39.910, radius: 800 }
const PLACED_LABEL_CSS = (color) => ({
  background: 'rgba(255,255,255,0.94)', color, fontSize: '11px', fontWeight: 600,
  padding: '1px 7px', borderRadius: '9px', whiteSpace: 'nowrap',
  border: `1px solid ${color}55`, boxShadow: '0 2px 6px -2px rgba(16,24,40,.25)'
})

onMounted(async () => {
  try { devices.value = await http.get('/devices') } catch (e) { /* noop */ }
  try {
    const s = await http.get('/drill/state')
    applyState(s)
  } catch (e) { /* noop */ }
  loadRuns()
  initMap()
  connectWs()
})

onUnmounted(() => {
  closeWs()
  if (mapApi) mapApi.destroy()
})

/* ---------- 地图 ---------- */
function initMap() {
  createMap(mapRef.value, { center: { lng: CORE.lng, lat: CORE.lat }, zoom: 12, view3d: false })
    .then((api) => {
      mapApi = api
      window.__drillMapApi = api
      mapLoading.value = false
      drawCoreZone()
      mapApi.onClick((p) => {
        // 点选放置模式:点击地图放置当前选中装备
        if (phase.value === 'IDLE' && placingId.value != null) {
          const d = devices.value.find((x) => x.id === placingId.value)
          if (d) addPlacement(d, p)
          placingId.value = null
        } else if (phase.value === 'RUNNING') {
          selEnemyId.value = null
        }
      })
      redrawAll()
    })
    .catch((e) => {
      mapLoading.value = false
      ElMessage.error(e.message || '地图加载失败,请到「地图管理」检查密钥配置')
    })
}

function drawCoreZone() {
  const r = state.value.coreRadius || CORE.radius
  const c = state.value.center || CORE
  overlays.core.push(mapApi.addCircle(c, r, {
    color: '#f04438', dashed: true, weight: 2, fillOpacity: 0.06, opacity: 0.8
  }))
  overlays.core.push(mapApi.addLabel(c, '<b>核心防护区</b><br/><span style="opacity:.7">半径 ' + r + 'm</span>', {
    dx: -60, dy: -14,
    css: { ...PLACED_LABEL_CSS('#f04438'), textAlign: 'center', lineHeight: '16px', padding: '4px 8px', borderRadius: '8px' }
  }))
}

function clearOverlayEntry(entry) {
  entry.marker.destroy()
  entry.label.destroy()
  if (entry.range) mapApi.remove(entry.range)
}

function drawPlacements(list) {
  if (!mapApi) return
  const seen = new Set()
  for (const p of list) {
    seen.add(p.deviceId)
    const color = deviceMeta(p.category).color
    let entry = overlays.placed.get(p.deviceId)
    if (entry && entry.pos !== `${p.lng},${p.lat},${p.scanRange}`) {
      clearOverlayEntry(entry)
      overlays.placed.delete(p.deviceId)
      entry = null
    }
    if (!entry) {
      const marker = mapApi.addMarker({ lng: p.lng, lat: p.lat }, { svg: deviceSvg(p.category, { online: true }), size: 40 })
      const label = mapApi.addLabel({ lng: p.lng, lat: p.lat }, `${p.name} · ${Math.round(p.scanRange)}m`, {
        dx: 22, dy: 24, css: PLACED_LABEL_CSS(color)
      })
      const range = mapApi.addCircle({ lng: p.lng, lat: p.lat }, p.scanRange, {
        color, dashed: true, weight: 1.5, fillOpacity: 0.05, opacity: 0.55
      })
      overlays.placed.set(p.deviceId, { marker, label, range, pos: `${p.lng},${p.lat},${p.scanRange}` })
    }
  }
  for (const [id, entry] of overlays.placed) {
    if (!seen.has(id)) {
      clearOverlayEntry(entry)
      overlays.placed.delete(id)
    }
  }
}

function drawEnemies(list) {
  if (!mapApi) return
  const seen = new Set()
  for (const e of list) {
    seen.add(e.id)
    const terminal = ['NEUTRALIZED', 'ESCAPED'].includes(e.status)
    let entry = overlays.enemies.get(e.id)
    if (!entry) {
      const marker = mapApi.addMarker({ lng: e.lng, lat: e.lat }, {
        rotate: true, color: ENEMY_COLOR[e.status] || ENEMY_COLOR.FLYING, size: 46,
        onClick: () => { selEnemyId.value = e.id }
      })
      const label = mapApi.addLabel({ lng: e.lng, lat: e.lat }, `${e.id} ${STATUS_TEXT[e.status] || ''}`, {
        dx: 16, dy: -12,
        css: { ...PLACED_LABEL_CSS(ENEMY_COLOR[e.status] || ENEMY_COLOR.FLYING) }
      })
      const track = mapApi.addPolyline(e.track || [], {
        color: ENEMY_COLOR[e.status] || ENEMY_COLOR.FLYING, weight: 3, opacity: 0.7
      })
      entry = { marker, label, track, color: ENEMY_COLOR[e.status] }
      overlays.enemies.set(e.id, entry)
    }
    entry.marker.update({ lng: e.lng, lat: e.lat, heading: e.heading })
    entry.label.setPosition({ lng: e.lng, lat: e.lat })
    if (entry.color !== ENEMY_COLOR[e.status]) {
      // 状态切换换色(重建图标与轨迹颜色近似即可)
      entry.marker.destroy()
      const marker = mapApi.addMarker({ lng: e.lng, lat: e.lat }, {
        rotate: true, color: ENEMY_COLOR[e.status], size: 46,
        onClick: () => { selEnemyId.value = e.id }
      })
      entry.marker = marker
      entry.color = ENEMY_COLOR[e.status]
      entry.label.setContent(`${e.id} ${STATUS_TEXT[e.status] || ''}`)
    }
    if (e.track && e.track.length >= 2) entry.track.setPath(e.track)
    entry.marker.update({ heading: e.heading })   // 换色重建后再补一次航向
    if (terminal) entry.label.setContent(`${e.id} ${e.outcome || STATUS_TEXT[e.status]}`)
  }
  for (const [id, entry] of overlays.enemies) {
    if (!seen.has(id)) {
      entry.marker.destroy()
      entry.label.destroy()
      entry.track.destroy()
      overlays.enemies.delete(id)
    }
  }
}

function clearEnemies() {
  for (const [, entry] of overlays.enemies) {
    entry.marker.destroy()
    entry.label.destroy()
    entry.track.destroy()
  }
  overlays.enemies.clear()
}

function redrawAll() {
  if (!mapApi) return
  if (phase.value === 'IDLE') {
    clearEnemies()
    drawPlacements(pre.value)
  } else {
    drawPlacements(state.value.placements || [])
    drawEnemies(state.value.enemies || [])
  }
}

/* ---------- 布防操作(IDLE) ---------- */
const isPlaced = (id) => pre.value.some((p) => p.deviceId === id)

function onDragStart(ev, d) {
  ev.dataTransfer.setData('text/plain', String(d.id))
  ev.dataTransfer.effectAllowed = 'copy'
}

function onDrop(ev) {
  if (phase.value !== 'IDLE') { ElMessage.warning('演练进行中,无法调整布防'); return }
  const id = Number(ev.dataTransfer.getData('text/plain'))
  const d = devices.value.find((x) => x.id === id)
  if (!d || !mapApi) return
  const rect = mapRef.value.getBoundingClientRect()
  const p = mapApi.toData(ev.clientX - rect.left, ev.clientY - rect.top)
  if (!p) { ElMessage.error('落点无法换算坐标'); return }
  addPlacement(d, p)
}

function togglePlacing(d) {
  placingId.value = placingId.value === d.id ? null : d.id
}

function addPlacement(d, p) {
  if (!COUNTER_META[d.category]) return
  const item = {
    deviceId: d.id, code: d.code, name: d.name, category: d.category,
    lng: +p.lng.toFixed(6), lat: +p.lat.toFixed(6),
    scanRange: rangeOf(d)
  }
  const i = pre.value.findIndex((x) => x.deviceId === d.id)
  if (i >= 0) pre.value.splice(i, 1, item)
  else pre.value.push(item)
  drawPlacements(pre.value)
}

function removePlacement(id) {
  pre.value = pre.value.filter((p) => p.deviceId !== id)
  drawPlacements(pre.value)
}

/** 一键按设备档案的部署位置布防全部反制设备 */
function autoPlace() {
  const list = counterDevices.value.filter((d) => d.homeLng && d.homeLat)
  if (!list.length) { ElMessage.warning('反制设备未配置部署坐标'); return }
  pre.value = list.map((d) => ({
    deviceId: d.id, code: d.code, name: d.name, category: d.category,
    lng: d.homeLng, lat: d.homeLat, scanRange: rangeOf(d)
  }))
  drawPlacements(pre.value)
  mapApi?.setViewport(pre.value.map((p) => ({ lng: p.lng, lat: p.lat })))
  ElMessage.success(`已布防 ${pre.value.length} 处装备`)
}

function clearPlacements() {
  pre.value = []
  drawPlacements(pre.value)
}

/* ---------- 演练控制 ---------- */
async function startDrill() {
  if (!pre.value.length) { ElMessage.warning('请先布防反制装备'); return }
  if (!pre.value.some((p) => COUNTER_META[p.category]?.role === 'detect')) {
    ElMessage.warning('布防至少需要 1 台探测类装备(警戒雷达/无线电探测/光电跟踪)')
    return
  }
  busy.value = true
  try {
    const s = await http.post('/drill/start', {
      placements: pre.value.map((p) => ({ deviceId: p.deviceId, lng: p.lng, lat: p.lat, scanRange: p.scanRange })),
      enemies: enemyCount.value,
      autoguard: autoguardOn.value
    })
    applyState(s)
    placingId.value = null
    mapApi?.setViewport([
      ...pre.value.map((p) => ({ lng: p.lng, lat: p.lat })),
      { lng: CORE.lng, lat: CORE.lat }
    ])
    ElMessage.success('演练开始,敌机已进入演练空域')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '启动失败')
  } finally { busy.value = false }
}

async function stopDrill() {
  try {
    await ElMessageBox.confirm('确认中止本次演练?', '中止演练', { type: 'warning' })
  } catch { return }
  busy.value = true
  try { applyState(await http.post('/drill/stop')) }
  catch (e) { ElMessage.error(e.response?.data?.message || e.message) }
  finally { busy.value = false }
}

async function resetDrill() {
  busy.value = true
  try { applyState(await http.post('/drill/reset')) }
  catch (e) { ElMessage.error(e.response?.data?.message || e.message) }
  finally { busy.value = false }
}

async function sendWave() {
  busy.value = true
  try {
    applyState(await http.post('/drill/wave', { count: waveCount.value, kind: waveKind.value || null }))
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message)
  } finally { busy.value = false }
}

async function setAutoguard(on) {
  try { applyState(await http.post('/drill/autoguard', { on })) }
  catch (e) { ElMessage.error(e.response?.data?.message || e.message) }
}

async function setSpeed(v) {
  try { applyState(await http.post('/drill/speed', { speed: v })) }
  catch (e) { ElMessage.error(e.response?.data?.message || e.message) }
}

async function engage(deviceId, enemyId) {
  try { applyState(await http.post('/drill/engage', { deviceId, enemyId })) }
  catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '处置失败')
  }
}

function onEoEngage({ deviceId, enemyId }) {
  engage(deviceId, enemyId)
}

async function loadRuns() {
  try { runs.value = await http.get('/drill/runs') } catch (e) { /* noop */ }
}

function showRun(r) {
  let detail = []
  try { detail = JSON.parse(r.detailJson || '[]') } catch (e) { /* ignore */ }
  ElNotification({
    title: `演练 #${r.id} 战果`,
    message: `评分 ${r.score} · 投放 ${r.enemiesTotal} / 探测 ${r.detected} / 处置 ${r.neutralized} / 逃脱 ${r.escaped}` +
      (detail.length ? `\n${detail.map((d) => `${d.id}:${d.outcome}`).join('  ')}` : ''),
    type: r.score >= 80 ? 'success' : 'info',
    duration: 6000
  })
}

/* ---------- 状态应用(REST/WS 汇入) ---------- */
let lastPhase = 'IDLE'
let lastEventId = 0

function applyState(s) {
  if (!s) return
  state.value = s
  redrawAll()

  // 阶段切换
  if (s.phase !== lastPhase) {
    if (s.phase === 'IDLE') {
      clearEnemies()
      drawPlacements(pre.value)
    }
    if (s.phase === 'ENDED' && lastPhase === 'RUNNING') {
      endDlg.data = s
      endDlg.open = true
      loadRuns()
    }
    lastPhase = s.phase
  }

  // 关键事件弹通知(探测首报/入侵告警)
  for (const ev of s.events || []) {
    if (ev.id <= lastEventId) continue
    lastEventId = ev.id
    if (ev.level === 'CRITICAL') {
      ElNotification({ title: '入侵告警', message: ev.text, type: 'error', duration: 5000 })
    }
  }
}

/* ---------- WebSocket(演练快照 1Hz) ---------- */
let ws = null
let wsTimer = null

function connectWs() {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${proto}://${location.host}/ws/telemetry`)
  ws.onopen = () => {
    if (wsTimer) { clearTimeout(wsTimer); wsTimer = null }
  }
  ws.onclose = () => { wsTimer = setTimeout(connectWs, 5000) }
  ws.onerror = () => ws.close()
  ws.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data)
      if (msg.type === 'drill') applyState(msg.payload)
    } catch (e) { /* ignore */ }
  }
}

function closeWs() {
  if (wsTimer) { clearTimeout(wsTimer); wsTimer = null }
  ws?.close()
}

/* ---------- 光电视窗 ---------- */
function openEo(p) {
  eoWin.placement = p
  eoWin.open = true
}

/* ---------- 格式化 ---------- */
function enemyTagType(s) {
  return { FLYING: 'danger', JAMMED: 'warning', CAPTURING: 'primary' }[s] || 'info'
}
function fmtResp(ms) {
  if (!ms) return '-'
  return (ms / 1000).toFixed(1) + 's'
}
function fmtElapsed(ms) {
  if (!ms) return '00:00'
  const s = Math.floor(ms / 1000)
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}
function fmtEvTime(ts) {
  const d = new Date(ts)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
}
function fmtRunTime(t) {
  return t ? String(t).replace('T', ' ').slice(5, 16) : '-'
}
</script>

<style scoped>
.drill-page {
  display: flex; gap: 12px;
  height: 100%; padding: 12px; box-sizing: border-box;
  background: #f2f4f7;
}

/* ---------- 左右栏 ---------- */
.side {
  width: 260px; flex-shrink: 0; display: flex; flex-direction: column;
  background: #fff; border-radius: 12px; padding: 10px;
  box-shadow: 0 1px 3px rgba(16,24,40,.08); overflow: hidden;
}
.side.left.collapsed { width: 44px; }
.side.left.collapsed .side-head { justify-content: center; }
.side-head {
  display: flex; align-items: center; justify-content: space-between;
  font-size: 13px; font-weight: 600; color: #1d2939; padding: 2px 4px;
}
.side-head.sec { border-top: 1px solid #eaecf0; margin-top: 8px; padding-top: 10px; }
.head-ops { display: flex; gap: 2px; }

.lib-tip { font-size: 11px; color: #98a2b3; padding: 4px; }
.lib-list { flex: 1; overflow-y: auto; }
.lib-item {
  display: flex; align-items: center; gap: 8px; padding: 7px 8px;
  border: 1px solid #eaecf0; border-radius: 10px; margin-bottom: 6px;
  cursor: grab; transition: all .15s; background: #fff;
}
.lib-item:hover { border-color: #b2ddff; background: #f8fbff; }
.lib-item.placing { border-color: #155eef; background: #eff6ff; box-shadow: 0 0 0 2px rgba(21,94,239,.12); }
.lib-item.placed { opacity: .82; }
.lib-icon { width: 34px; height: 34px; flex-shrink: 0; }
.lib-info { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.lib-info b { font-size: 12px; color: #1d2939; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lib-info span { font-size: 11px; color: #98a2b3; }
.lib-empty { font-size: 11px; color: #98a2b3; padding: 8px; line-height: 1.6; }

.placed-list { max-height: 190px; overflow-y: auto; margin-top: 6px; }
.placed-item {
  display: flex; align-items: center; gap: 6px; padding: 6px 6px;
  border-radius: 8px; font-size: 12px;
}
.placed-item:hover { background: #f9fafb; }
.placed-item .dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.placed-info { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.placed-info b { font-size: 12px; color: #344054; }
.placed-info span { font-size: 10px; color: #98a2b3; }

/* ---------- 地图区 ---------- */
.map-area { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 10px; }
.drill-toolbar {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  background: #fff; border-radius: 12px; padding: 8px 14px;
  box-shadow: 0 1px 3px rgba(16,24,40,.08);
}
.tb-label { font-size: 12px; color: #475467; }
.tb-stats { margin-left: auto; display: flex; gap: 14px; font-size: 12px; color: #667085; }
.tb-stats b { color: #1d2939; font-family: Consolas, monospace; }
.c-red { color: #d92d20 !important; }
.c-green { color: #12b76a !important; }
.c-orange { color: #f79009 !important; }

.map-box { flex: 1; position: relative; border-radius: 12px; overflow: hidden; box-shadow: 0 1px 3px rgba(16,24,40,.08); }
.map-container { position: absolute; inset: 0; }
.map-loading { position: absolute; inset: 0; background: rgba(255,255,255,.6); }

.legend {
  position: absolute; left: 12px; bottom: 12px; display: flex; gap: 12px;
  background: rgba(255,255,255,.92); border-radius: 10px; padding: 6px 12px;
  font-size: 11px; color: #475467; box-shadow: 0 2px 8px -2px rgba(16,24,40,.2);
}
.legend span { display: flex; align-items: center; gap: 4px; }
.legend .dot { width: 9px; height: 9px; border-radius: 50%; }
.legend .ring { width: 10px; height: 10px; border-radius: 50%; border: 2px dashed #f04438; }

/* ---------- 敌机处置卡 ---------- */
.enemy-card {
  position: absolute; left: 12px; top: 12px; width: 300px;
  background: rgba(255,255,255,.97); border-radius: 12px; padding: 10px 12px;
  box-shadow: 0 8px 24px -8px rgba(16,24,40,.3);
}
.ec-head { display: flex; align-items: center; gap: 8px; }
.ec-head b { font-size: 14px; color: #d92d20; font-family: Consolas, monospace; }
.ec-kind { font-size: 11px; color: #98a2b3; }
.ec-close { margin-left: auto; }
.ec-body { display: flex; flex-wrap: wrap; gap: 6px 12px; margin: 8px 0; font-size: 12px; color: #667085; }
.ec-body b { color: #1d2939; font-family: Consolas, monospace; }
.ec-ops { display: flex; flex-wrap: wrap; gap: 6px; }
.ec-none { font-size: 11px; color: #98a2b3; }

/* ---------- 右栏事件流 ---------- */
.side.right { width: 300px; }
.event-feed { flex: 1; overflow-y: auto; padding: 6px 4px; }
.event-item { display: flex; gap: 8px; padding: 6px 6px; border-radius: 8px; margin-bottom: 4px; }
.event-item:hover { background: #f9fafb; }
.ev-dot { width: 8px; height: 8px; border-radius: 50%; margin-top: 5px; flex-shrink: 0; background: #98a2b3; }
.event-item.lv-critical .ev-dot { background: #d92d20; box-shadow: 0 0 0 3px rgba(217,45,32,.15); }
.event-item.lv-warning .ev-dot { background: #f79009; }
.event-item.lv-success .ev-dot { background: #12b76a; }
.event-item.lv-info .ev-dot { background: #0ba5ec; }
.event-item.lv-critical .ev-text { color: #b42318; }
.event-item.lv-warning .ev-text { color: #b54708; }
.event-item.lv-success .ev-text { color: #087443; }
.ev-text { font-size: 12px; color: #344054; line-height: 1.5; }
.ev-time { font-size: 10px; color: #b6bec9; font-family: Consolas, monospace; margin-top: 2px; }

.runs-list { max-height: 220px; overflow-y: auto; margin-top: 6px; }
.run-item { padding: 6px 8px; border-radius: 8px; cursor: pointer; }
.run-item:hover { background: #f9fafb; }
.run-line1 { display: flex; align-items: center; gap: 6px; font-size: 12px; }
.run-score { margin-left: auto; color: #155eef; font-family: Consolas, monospace; }
.run-line2 { font-size: 11px; color: #98a2b3; margin-top: 2px; }

/* ---------- 结束汇总 ---------- */
.end-score { text-align: center; padding: 8px 0 2px; }
.score-num { font-size: 44px; font-weight: 700; font-family: Consolas, monospace; line-height: 1.1; }
.score-label { font-size: 12px; color: #98a2b3; }
.end-stats { display: flex; justify-content: space-around; margin-top: 12px; }
.es-item { text-align: center; }
.es-item b { display: block; font-size: 20px; color: #1d2939; font-family: Consolas, monospace; }
.es-item span { font-size: 11px; color: #98a2b3; }
</style>
