<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">飞行任务</span>
      <div class="actions">
        <el-radio-group v-model="statusFilter">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="PENDING">待执行</el-radio-button>
          <el-radio-button value="FLYING">执行中</el-radio-button>
          <el-radio-button value="COMPLETED">已完成</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="openDialog()">新建任务</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <el-table :data="filtered" v-loading="loading" stripe height="100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="任务名称" min-width="180">
          <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column label="无人机" width="140">
          <template #default="{ row }"><span class="code">{{ row.drone?.code || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="飞手" width="80">
          <template #default="{ row }">{{ row.pilot?.name || '-' }}</template>
        </el-table-column>
        <el-table-column prop="plannedAltitude" label="航高(m)" width="90" />
        <el-table-column prop="plannedDuration" label="时长(min)" width="100" />
        <el-table-column label="审批" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="approvalType[row.approval]" effect="dark">{{ approvalText[row.approval] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="status" :class="'ts-' + row.status.toLowerCase()"><i></i>{{ statusText[row.status] }}</span>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="150">
          <template #default="{ row }">{{ fmt(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.approval === 'PENDING'" link type="warning" size="small" @click="approve(row, 'approved')">批准</el-button>
            <el-button v-if="row.approval === 'PENDING'" link type="danger" size="small" @click="approve(row, 'rejected')">驳回</el-button>
            <el-button v-if="row.approval === 'APPROVED' && row.status === 'PENDING'" link type="primary" size="small" @click="launch(row)">
              <el-icon style="margin-right:2px"><VideoPlay /></el-icon>下发起飞
            </el-button>
            <el-button v-if="row.status === 'FLYING'" link type="danger" size="small" @click="abort(row)">中止</el-button>
            <el-button v-if="row.routeJson" link size="small" @click="showRoute(row)">航线</el-button>
            <el-popconfirm v-if="row.status !== 'FLYING'" title="确认删除该任务?" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新建任务 -->
    <el-dialog v-model="dialog.visible" title="新建飞行任务" width="640px">
      <el-form :model="dialog.form" label-width="92px">
        <el-form-item label="任务名称" required>
          <el-input v-model="dialog.form.name" placeholder="如:朝阳区河道巡查" />
        </el-form-item>
        <el-form-item label="任务说明">
          <el-input v-model="dialog.form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="无人机" required>
              <el-select v-model="dialog.form.droneId" style="width: 100%" placeholder="选择无人机">
                <el-option v-for="d in drones" :key="d.id" :label="d.code + ' · ' + d.model" :value="d.id"
                           :disabled="d.status === 'MAINTENANCE' || d.status === 'OFFLINE'" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="飞手">
              <el-select v-model="dialog.form.pilotId" clearable style="width: 100%" placeholder="默认取无人机绑定飞手">
                <el-option v-for="p in pilots" :key="p.id" :label="p.name + ' · ' + p.org" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="航高(m)">
              <el-input-number v-model="dialog.form.plannedAltitude" :min="20" :max="500" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="时长(min)">
              <el-input-number v-model="dialog.form.plannedDuration" :min="5" :max="180" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="航线">
          <div class="route-box">
            <el-button size="small" @click="pickOnMap">地图选点({{ routePoints.length }}/5)</el-button>
            <span class="route-tip">点击后在北京地图上依次点击取点,自动首尾闭合</span>
          </div>
          <div v-if="routePoints.length" class="route-preview">
            <el-tag v-for="(p, i) in routePoints" :key="i" size="small" style="margin: 2px">
              {{ p.lng.toFixed(3) }},{{ p.lat.toFixed(3) }}
            </el-tag>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">提交审批</el-button>
      </template>
    </el-dialog>

    <!-- 航线查看 -->
    <el-dialog v-model="routeVisible" title="任务航线" width="640px">
      <div ref="routeMapRef" class="route-map"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { VideoPlay } from '@element-plus/icons-vue'
import http from '../api'
import { DARK_MAP_STYLE, BMAP_AK } from '../utils/map'
import { createMap, routePointSvg } from '../utils/mapAdapter'

const statusText = { PENDING: '待执行', FLYING: '执行中', COMPLETED: '已完成', ABORTED: '已中止' }
const approvalText = { PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回' }
const approvalType = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }

const loading = ref(false)
const statusFilter = ref('')
const tasks = ref([])
const drones = ref([])
const pilots = ref([])
const routePoints = ref([])
const picking = ref(false)

const dialog = reactive({ visible: false, saving: false, form: {} })
const routeVisible = ref(false)
const routeMapRef = ref(null)

const filtered = computed(() => tasks.value.filter((t) => !statusFilter.value || t.status === statusFilter.value))

function fmt(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

onMounted(load)
async function load() {
  loading.value = true
  try {
    ;[tasks.value, drones.value, pilots.value] = await Promise.all([
      http.get('/tasks'), http.get('/drones'), http.get('/pilots')
    ])
  } finally { loading.value = false }
}

function openDialog() {
  dialog.form = { name: '', description: '', droneId: null, pilotId: null, plannedAltitude: 100, plannedDuration: 20 }
  routePoints.value = []
  dialog.visible = true
}

/* 地图选点弹窗 */
function pickOnMap() {
  doPick()
}

function doPick() {
  picking.value = true
  const win = window.open('', '_blank', 'width=900,height=680')
  if (!win) { ElMessage.warning('弹窗被拦截,请允许弹窗后重试'); picking.value = false; return }

  win.document.write(`<!DOCTYPE html><html><head><meta charset="utf-8"><title>航线选点</title>
    <style>body{margin:0;font-family:sans-serif}#tip{position:fixed;top:10px;left:10px;z-index:99;background:#0a1428;color:#7fd4ff;padding:8px 14px;border-radius:8px;border:1px solid #1c4a7d;font-size:13px}</style>
    </head><body><div id="tip">依次点击地图取点(至少 2 个),完成后自动关闭</div><div id="map" style="width:100vw;height:100vh"></div>
    <script src="https://api.map.baidu.com/api?v=1.0&type=webgl&ak=${BMAP_AK}"><\/script>
    <script>
    const map = new BMapGL.Map(document.getElementById('map'))
    map.centerAndZoom(new BMapGL.Point(116.404, 39.925), 12)
    map.enableScrollWheelZoom(true)
    map.setMapStyleV2({styleJson: ${JSON.stringify(DARK_MAP_STYLE.styleJson)}})
    const pts = []
    let line = null
    map.addEventListener('click', (e) => {
      const p = { lng: +e.point.lng.toFixed(6), lat: +e.point.lat.toFixed(6) }
      pts.push(p)
      const m = new BMapGL.Marker(e.point)
      map.addOverlay(m)
      if (line) map.removeOverlay(line)
      if (pts.length >= 2) line = new BMapGL.Polyline(pts.map(x => new BMapGL.Point(x.lng, x.lat)), {strokeColor:'#155eef',strokeWeight:3})
      if (line) map.addOverlay(line)
      document.getElementById('tip').textContent = '已选 ' + pts.length + ' 个点(点击右键完成)'
    })
    map.addEventListener('rightclick', () => {
      if (pts.length >= 2) {
        localStorage.setItem('__route_points', JSON.stringify(pts))
        window.close()
      }
    })
    <\/script></body></html>`)
  win.document.close()

  // 轮询取结果(窗口关闭时读 localStorage)
  const timer = setInterval(() => {
    if (win.closed) {
      clearInterval(timer)
      try {
        const raw = win.localStorage.getItem('__route_points') || localStorage.getItem('__route_points')
        if (raw) {
          routePoints.value = JSON.parse(raw)
          localStorage.setItem('__route_points', raw)
          ElMessage.success(`已选取 ${routePoints.value.length} 个航点`)
        }
      } catch (e) { /* noop */ }
      picking.value = false
    }
  }, 500)
}

async function save() {
  const f = dialog.form
  if (!f.name) return ElMessage.warning('请输入任务名称')
  if (!f.droneId) return ElMessage.warning('请选择无人机')
  dialog.saving = true
  try {
    const route = routePoints.value.length >= 2
      ? [...routePoints.value, routePoints.value[0]].map((p) => ({ ...p, alt: f.plannedAltitude }))
      : null
    await http.post('/tasks', {
      ...f,
      drone: { id: f.droneId },
      pilot: f.pilotId ? { id: f.pilotId } : null,
      routeJson: route ? JSON.stringify(route) : null
    })
    ElMessage.success('任务已提交,待审批')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function approve(row, result) {
  await http.post(`/tasks/${row.id}/approve?result=${result}`)
  ElMessage.success(result === 'approved' ? '已批准' : '已驳回')
  load()
}

async function launch(row) {
  await http.post(`/tasks/${row.id}/launch`)
  ElMessage.success(`任务「${row.name}」已下发,无人机起飞`)
  load()
}

async function abort(row) {
  await http.post(`/tasks/${row.id}/abort`)
  ElMessage.warning('任务已中止')
  load()
}

async function remove(id) {
  await http.delete(`/tasks/${id}`)
  ElMessage.success('已删除')
  load()
}

/* 航线查看地图(适配层:随「地图管理」当前提供商切换) */
let routeMapApi = null

async function showRoute(row) {
  routeVisible.value = true
  await nextTick()
  const pts = JSON.parse(row.routeJson)
  routeMapApi?.destroy()
  try {
    routeMapApi = await createMap(routeMapRef.value, { center: pts[0], zoom: 13, customStyle: true })
  } catch (e) {
    ElMessage.error(e.message || '地图加载失败')
    return
  }
  routeMapApi.addPolygon(pts, { color: '#155eef', weight: 3, fill: '#2f7bff', fillOpacity: 0.12 })
  pts.forEach((p) => {
    routeMapApi.addMarker(p, { svg: routePointSvg(), size: 18 })
  })
  routeMapApi.setViewport(pts)
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px); padding: 8px; }
.actions { display: flex; gap: 10px; }
.name { font-weight: 600; }
.code { color: var(--primary); font-size: 13px; }

.status { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; }
.status i { width: 7px; height: 7px; border-radius: 50%; }
.ts-pending i { background: #5d76a8; }
.ts-flying i { background: #155eef; box-shadow: 0 0 8px #155eef; animation: pulse-glow 1.6s infinite; }
.ts-completed i { background: #12b76a; }
.ts-aborted i { background: #f04438; }

.route-box { display: flex; align-items: center; gap: 10px; }
.route-tip { font-size: 12px; color: var(--text-dim); }
.route-preview { margin-top: 8px; }
.route-map { width: 100%; height: 420px; border-radius: 8px; overflow: hidden; }

:deep(.el-radio-button__inner) { background: transparent; }
</style>
