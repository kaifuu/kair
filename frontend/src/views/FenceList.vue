<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">电子围栏</span>
      <el-button type="primary" @click="openDialog()">新增围栏</el-button>
    </div>

    <div class="fence-body">
      <div class="panel table-panel">
        <el-table :data="fences" v-loading="loading" stripe height="100%"
                  highlight-current-row @current-change="onRowClick">
          <el-table-column prop="name" label="名称" min-width="170">
            <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
          </el-table-column>
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <span class="ftype" :class="'ft-' + row.type.toLowerCase()">
                <i></i>{{ typeText[row.type] }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="形状" width="90">
            <template #default="{ row }">{{ shapeCell(row) }}</template>
          </el-table-column>
          <el-table-column label="限高(m)" width="90">
            <template #default="{ row }">{{ row.type === 'NO_FLY' ? '-' : row.maxAltitude }}</template>
          </el-table-column>
          <el-table-column label="半径(m)" width="90">
            <template #default="{ row }">{{ row.shape === 'POLYGON' || row.shape === 'MULTI' ? '-' : (row.radius || '-') }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
          <el-table-column label="启用" width="80">
            <template #default="{ row }">
              <el-switch v-model="row.enabled" @change="toggle(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click.stop="openDialog(row)">编辑</el-button>
              <el-popconfirm title="确认删除该围栏?" @confirm="remove(row.id)">
                <template #reference>
                  <el-button link type="danger" size="small" @click.stop>删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="panel map-panel">
        <div ref="mapPanelRef" class="map-host"><div ref="mapRef" class="map"></div></div>
      </div>
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.form.id ? '编辑围栏' : '新增围栏'" width="580px">
      <el-form :model="dialog.form" label-width="92px">
        <el-form-item label="名称" required>
          <el-input v-model="dialog.form.name" placeholder="如:XX政府禁区" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="类型">
              <el-select v-model="dialog.form.type" style="width: 100%">
                <el-option label="禁飞区" value="NO_FLY" />
                <el-option label="限飞区" value="LIMIT" />
                <el-option label="作业区" value="WORK" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="形状">
              <el-select v-model="dialog.form.shape" style="width: 100%">
                <el-option label="复合(多区域)" value="MULTI" />
                <el-option label="圆形" value="CIRCLE" />
                <el-option label="多边形" value="POLYGON" />
                <el-option label="线形" value="LINE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="限高(m)" v-if="dialog.form.type !== 'NO_FLY'">
              <el-input-number v-model="dialog.form.maxAltitude" :min="0" :max="1000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="dialog.form.shape !== 'POLYGON' && dialog.form.shape !== 'MULTI'"
                          :label="dialog.form.shape === 'LINE' ? '走廊(m)' : '半径(m)'">
              <el-input-number v-model="dialog.form.radius" :min="100" :max="50000" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="绘制范围">
          <div class="draw-field">
            <div class="draw-entry">
              <el-button type="primary" plain @click="openDraw">
                <el-icon style="margin-right: 5px"><Location /></el-icon>在地图上绘制
              </el-button>
              <span class="draw-summary" :class="{ ok: drawSummary.ok }">{{ drawSummary.text }}</span>
            </div>
            <div class="coord-tip">在地图上单击勾画,系统自动记录坐标;线形/圆形以「走廊/半径」向两侧扩展;复合围栏可绘制多个区域,一次保存共用一个开关</div>
            <el-collapse class="json-collapse">
              <el-collapse-item title="高级:直接编辑坐标 JSON" name="json">
                <el-input v-model="dialog.form.pointsJson" type="textarea" :rows="4" :placeholder="pointsPlaceholder" />
              </el-collapse-item>
            </el-collapse>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dialog.form.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 全屏地图绘制:打开时把主图 DOM 挪入对话框(全程单一地图实例,规避 BMapGL 多实例互相污染) -->
    <el-dialog v-model="drawDlg.visible" fullscreen class="draw-dlg"
               :title="'地图绘制 · ' + (dialog.form.name || '未命名围栏')">
      <div class="draw-wrap">
        <div ref="drawMountRef" class="draw-map"></div>

        <!-- 编辑模式(单形状围栏):画完回填表单 -->
        <div v-if="dialog.form.id && dialog.form.shape !== 'MULTI'" class="draw-toolbar">
          <el-radio-group v-model="drawShape" size="small" @change="restartDraw">
            <el-radio-button value="CIRCLE">圆形</el-radio-button>
            <el-radio-button value="POLYGON">多边形</el-radio-button>
            <el-radio-button value="LINE">线形</el-radio-button>
          </el-radio-group>
          <span class="draw-hint">{{ drawHint }}</span>
          <el-button size="small" @click="undoPoint">撤销</el-button>
          <el-button size="small" @click="clearPoints">清空</el-button>
          <el-button size="small" type="primary" @click="finishDraw">完成绘制</el-button>
          <el-button size="small" @click="drawDlg.visible = false">取消</el-button>
        </div>

        <!-- 新增 / 复合围栏编辑:连续绘制多个区域,合并为一条围栏记录(共用一个开关) -->
        <template v-else>
          <div class="draw-toolbar">
            <el-input v-model="dialog.form.name" size="small" clearable placeholder="围栏名称"
                      style="width: 170px" />
            <el-select v-model="dialog.form.type" size="small" style="width: 96px">
              <el-option label="禁飞区" value="NO_FLY" />
              <el-option label="限飞区" value="LIMIT" />
              <el-option label="作业区" value="WORK" />
            </el-select>
            <el-radio-group v-model="drawShape" size="small" @change="restartDraw">
              <el-radio-button value="CIRCLE">圆形</el-radio-button>
              <el-radio-button value="POLYGON">多边形</el-radio-button>
              <el-radio-button value="LINE">线形</el-radio-button>
            </el-radio-group>
            <span class="draw-hint">{{ drawHint }}</span>
            <el-button size="small" @click="undoPoint">撤销</el-button>
            <el-button size="small" @click="clearPoints">清空</el-button>
            <el-button size="small" type="primary" :loading="drawDlg.savingAll" @click="saveAllDrawn">
              {{ dialog.form.id ? '保存修改' : '保存全部' }}({{ drawnShapes.length }})
            </el-button>
            <el-button size="small" @click="drawDlg.visible = false">关闭</el-button>
          </div>

          <div v-if="drawnShapes.length" class="draw-shapes">
            <div class="ds-head">已绘制 {{ drawnShapes.length }} 个区域</div>
            <div class="ds-item" v-for="(s, i) in drawnShapes" :key="i">
              <i :style="{ background: colors[dialog.form.type] }"></i>
              <span class="ds-text">
                {{ i + 1 }}. {{ shapeText[s.shape] }}
                {{ s.shape === 'CIRCLE' ? `· r${Math.round(s.radius)}m` : `· ${s.points.length} 点` }}
              </span>
              <el-button link type="danger" size="small" @click="removeShape(i)">删除</el-button>
            </div>
          </div>
        </template>

        <div class="draw-status">
          <span>当前:已选 <b>{{ drawStatus.points }}</b> 个点</span>
          <span v-if="drawShape !== 'POLYGON'">
            {{ drawShape === 'LINE' ? '走廊' : '半径' }} <b>{{ Math.round(drawStatus.radius) }}</b> m
          </span>
          <span v-if="!dialog.form.id || dialog.form.shape === 'MULTI'">已完成 <b>{{ drawnShapes.length }}</b> 个区域</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Location } from '@element-plus/icons-vue'
import http from '../api'
import { createMap } from '../utils/mapAdapter'
import { parseFenceShapes } from '../utils/map'

const typeText = { NO_FLY: '禁飞区', LIMIT: '限飞区', WORK: '作业区' }
const shapeText = { CIRCLE: '圆形', LINE: '线形', POLYGON: '多边形', MULTI: '复合' }
const colors = { NO_FLY: '#f04438', LIMIT: '#f79009', WORK: '#12b76a' }

/** 表格形状列:复合围栏显示区域数 */
function shapeCell(row) {
  if (row.shape !== 'MULTI') return shapeText[row.shape] || '多边形'
  const n = parseFenceShapes(row).length
  return n > 1 ? `复合 ${n} 区域` : '复合'
}

/** 把一个几何部件渲染到地图实例,返回覆盖物数组(围栏渲染统一出口) */
function renderPartTo(api, part, style) {
  const out = []
  if (part.shape === 'CIRCLE' && part.radius && part.points[0]) {
    out.push(api.addCircle(part.points[0], part.radius, style))
  } else if (part.shape === 'LINE' && part.points.length >= 2 && part.radius) {
    out.push(api.addPolyline(part.points, { ...style, weight: Math.max(2, Math.round(part.radius / 60)), opacity: 0.18 }))
    out.push(api.addPolyline(part.points, { ...style, weight: 2.5 }))
  } else if (part.points.length >= 3) {
    out.push(api.addPolygon(part.points, style))
  }
  return out.filter(Boolean)
}

const pointsPlaceholder = `[{"lng":116.397,"lat":39.910}]`

const loading = ref(false)
const fences = ref([])
const mapRef = ref(null)

const dialog = reactive({ visible: false, saving: false, form: {} })

let mapApi = null
let mapInit = null // createMap promise:冷启动(SDK+GL 引擎)慢网可达 10s+,openDraw 等它而非立刻报错
let overlays = []

onMounted(async () => {
  fences.value = await http.get('/fences')
  mapInit = createMap(mapRef.value, { center: { lng: 116.404, lat: 39.925 }, zoom: 10, customStyle: true })
    .then((api) => { mapApi = api; redraw() })
    .catch((e) => { ElMessage.error(e.message || '地图加载失败,请到「地图管理」检查密钥配置') })
})

onUnmounted(() => mapApi?.destroy())

function redraw() {
  if (!mapApi) return
  overlays.forEach((o) => mapApi.remove(o))
  overlays = []
  fences.value.forEach((f) => {
    if (!f.enabled) return
    const parts = parseFenceShapes(f)
    if (!parts.length) return
    const color = colors[f.type]
    parts.forEach((p) => {
      overlays.push(...renderPartTo(mapApi, p, { color, dashed: true, fillOpacity: 0.14, opacity: 0.9 }))
    })
  })
}

function onRowClick(row) {
  if (!row || !mapApi) return
  const parts = parseFenceShapes(row)
  if (!parts.length) return
  const all = parts.flatMap((p) => p.points)
  if (parts[0].shape === 'CIRCLE' && parts[0].points[0]) {
    mapApi.flyTo(parts[0].points[0], 12)
  } else if (all.length) {
    mapApi.setViewport(all)
  }
}

function openDialog(row) {
  dialog.form = row
    ? { ...row }
    : { name: '', type: 'NO_FLY', shape: 'CIRCLE', radius: 3000, maxAltitude: 0, pointsJson: '[{"lng":116.397,"lat":39.910}]', remark: '' }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.name) return ElMessage.warning('请输入名称')
  const parts = parseFenceShapes(f)
  if (!parts.length) return ElMessage.error('坐标 JSON 格式错误或没有坐标点')
  if (f.shape === 'MULTI') {
    // 复合围栏:逐部件校验
    if (!parts.every((p) => p.points.length >= 1)) return ElMessage.warning('存在没有坐标的部件')
    if (parts.some((p) => p.shape === 'CIRCLE' && !p.radius)) return ElMessage.warning('圆形部件缺少半径')
    if (parts.some((p) => p.shape === 'LINE' && !p.radius)) return ElMessage.warning('线形部件缺少走廊宽度')
  } else {
    if (parts.length > 1 || parts[0].shape !== f.shape) {
      return ElMessage.warning('坐标为复合围栏格式:形状请选择「复合(多区域)」,或通过「在地图上绘制」编辑')
    }
    const pts = parts[0].points
    const need = f.shape === 'CIRCLE' ? 1 : f.shape === 'LINE' ? 2 : 3
    if (pts.length < need) {
      return ElMessage.warning(f.shape === 'CIRCLE'
        ? '圆形需要 1 个中心点,请点击「在地图上绘制」'
        : `${shapeText[f.shape] || '该形状'}至少需要 ${need} 个点,请点击「在地图上绘制」`)
    }
    if (f.shape !== 'POLYGON' && !f.radius) return ElMessage.warning('请填写半径/走廊(米)')
  }
  dialog.saving = true
  try {
    if (f.id) await http.put(`/fences/${f.id}`, f)
    else await http.post('/fences', f)
    ElMessage.success('保存成功')
    dialog.visible = false
    fences.value = await http.get('/fences')
    redraw()
  } finally { dialog.saving = false }
}

async function toggle(row) {
  await http.put(`/fences/${row.id}`, { enabled: row.enabled })
  ElMessage.success(row.enabled ? '已启用' : '已停用')
  redraw()
}

async function remove(id) {
  await http.delete(`/fences/${id}`)
  ElMessage.success('已删除')
  fences.value = await http.get('/fences')
  redraw()
}

/* ---------- 地图可视化绘制(复用主图实例,打开时把 DOM 挪进全屏对话框) ---------- */
const drawDlg = reactive({ visible: false, savingAll: false })
const drawMountRef = ref(null)
const mapPanelRef = ref(null)
const drawStatus = reactive({ points: 0, radius: 0 })
const drawnShapes = ref([])   // 多区域模式已完成的形状 [{shape, points, radius, overlays[]}]
const drawShape = ref('CIRCLE')   // 当前绘制的部件形状(独立于围栏本体 shape,避免覆写 MULTI)

/** 多区域模式:新增围栏,或编辑复合(MULTI)围栏 */
const multiMode = () => !dialog.form.id || dialog.form.shape === 'MULTI'

const drawHint = computed(() => ({
  CIRCLE: '单击定圆心 → 移动鼠标预览 → 再单击定半径',
  POLYGON: '依次单击添加顶点(至少 3 个),双击或「完成绘制」结束',
  LINE: '依次单击添加拐点(至少 2 个),双击或「完成绘制」结束;走廊为向两侧扩展的宽度'
}[drawShape.value] || ''))

const drawSummary = computed(() => {
  const f = dialog.form
  const parts = parseFenceShapes(f)
  if (f.shape === 'MULTI') {
    return parts.length ? { text: `复合围栏 · ${parts.length} 个区域`, ok: true } : { text: '尚未绘制区域', ok: false }
  }
  if (!parts.length || !parts[0].points.length) return { text: '尚未绘制', ok: false }
  if (f.shape === 'CIRCLE') return { text: `圆心 1 点 · 半径 ${f.radius || '-'} m`, ok: true }
  return { text: `已绘制 ${parts[0].points.length} 个点`, ok: true }
})

async function openDraw() {
  if (!mapApi && mapInit) await mapInit // 引擎冷启动中,等初始化落定(成功或失败)再继续
  if (!mapApi) {
    ElMessage.error('地图尚未加载,请稍后重试或到「地图管理」检查密钥配置')
    return
  }
  drawDlg.visible = true
  drawDlg.savingAll = false
  clearDrawSession()
  drawShape.value = dialog.form.shape === 'MULTI' ? 'CIRCLE' : (dialog.form.shape || 'CIRCLE')
  await nextTick()
  // 等 canvas 就绪再搬:百度 GL 冷启动要串行拉取约 18 个 wasm worker(慢网 10s+),
  // 初始化中途挪动容器会令渲染引擎夭折(canvas 永不出现)
  await waitMapRendered()
  if (!drawDlg.visible) return // 等待期间用户已关闭对话框
  // 主图 DOM 挪进全屏对话框,BMapGL 监听 window resize 自适应新尺寸
  drawMountRef.value?.appendChild(mapRef.value)
  requestAnimationFrame(() => window.dispatchEvent(new Event('resize')))
  drawReferences()
  if (dialog.form.id && dialog.form.shape === 'MULTI') preloadMultiParts()
  startDrawing()
}

/** 天地图为瓦片 img 无 canvas,其余引擎以 canvas 出现为渲染就绪信号 */
async function waitMapRendered(timeoutMs = 30000) {
  if (mapApi.provider === 'tdt') return
  const el = mapRef.value
  const t0 = Date.now()
  while (Date.now() - t0 < timeoutMs) {
    if (!el || el.querySelector('canvas')) return
    await new Promise((r) => setTimeout(r, 200))
  }
}

/** 清掉上一次绘制会话的残留(参考围栏 + 已绘部件) */
let refOverlays = []
function clearDrawSession() {
  drawnShapes.value.forEach((s) => s?.overlays.forEach((o) => mapApi?.remove(o)))
  refOverlays.forEach((o) => mapApi?.remove(o))
  refOverlays = []
  drawnShapes.value = []
}

/** 编辑复合围栏:已保存的部件载入待编辑列表,可继续增删后整体保存 */
function preloadMultiParts() {
  const f = dialog.form
  drawnShapes.value = parseFenceShapes(f).map((p) => ({
    shape: p.shape,
    points: (p.points || []).map((q) => ({ lng: +q.lng, lat: +q.lat })),
    radius: p.radius ?? null,
    overlays: []
  }))
  const style = { color: colors[f.type] || '#155eef', weight: 2.5, opacity: 0.95, fillOpacity: 0.14 }
  drawnShapes.value.forEach((s) => { s.overlays = renderPartTo(mapApi, s, style) })
  const all = drawnShapes.value.flatMap((s) => s.points)
  if (all.length) mapApi.setViewport(all)
}

/** 绘制底图参考:已有围栏淡显,正在编辑的围栏按类型色高亮并定位(复合围栏由 preloadMultiParts 单独载入) */
function drawReferences() {
  fences.value.forEach((f) => {
    if (f.id === dialog.form.id && f.shape === 'MULTI') return
    const parts = parseFenceShapes(f)
    if (!parts.length) return
    const editing = f.id === dialog.form.id
    const color = editing ? (colors[f.type] || '#155eef') : '#98a2b3'
    const st = { color, weight: editing ? 2.5 : 1.5, opacity: editing ? 0.95 : 0.4, fillOpacity: 0.06, dashed: !editing }
    parts.forEach((p) => { refOverlays.push(...renderPartTo(mapApi, p, st)) })
    if (editing) mapApi.flyTo(parts[0].points[0], 12)
  })
}

function startDrawing() {
  mapApi?.startDraw({
    shape: drawShape.value,
    keep: multiMode(),   // 多区域模式:完成不销毁,连续绘制多个区域
    onUpdate: (pts, r) => { drawStatus.points = pts.length; drawStatus.radius = r },
    onFinish: applyDrawn
  })
  drawStatus.points = 0
  drawStatus.radius = 0
}

function restartDraw() {
  if (mapApi) startDrawing()
}

function undoPoint() {
  // 当前有未完成顶点则撤顶点;否则删除上一个已完成的区域
  if (drawStatus.points > 0) return mapApi?.undoDrawPoint()
  if (drawnShapes.value.length) {
    removeShape(drawnShapes.value.length - 1)
    ElMessage.success('已删除上一个区域')
  }
}
function clearPoints() { mapApi?.clearDraw() }

function finishDraw() {
  if (!mapApi?.finishDraw()) {
    ElMessage.warning(drawShape.value === 'CIRCLE'
      ? '请先单击定圆心,移动鼠标预览,再单击定半径'
      : `至少需要 ${drawShape.value === 'LINE' ? 2 : 3} 个点`)
  }
}

/** 单形状编辑模式:画完回填表单并关闭 */
function applyDrawn(pts, radius, bundle) {
  const rounded = pts.map((p) => ({ lng: +p.lng.toFixed(6), lat: +p.lat.toFixed(6) }))
  if (!multiMode() || !bundle) {
    dialog.form.shape = drawShape.value   // 单形状编辑允许切换形状,回填时同步表单
    dialog.form.pointsJson = JSON.stringify(rounded)
    if (drawShape.value === 'CIRCLE') dialog.form.radius = Math.max(100, Math.round(radius))
    drawDlg.visible = false
    ElMessage.success(drawShape.value === 'CIRCLE'
      ? `已绘制圆形,半径 ${Math.round(radius)} m`
      : `已绘制 ${pts.length} 个坐标点`)
    return
  }
  // 多区域模式:累计部件,继续绘制下一个
  drawnShapes.value.push({
    shape: drawShape.value,
    points: rounded,
    radius: drawShape.value === 'CIRCLE' ? Math.max(100, Math.round(radius)) : null,
    overlays: bundle
  })
  ElMessage.success(`已完成第 ${drawnShapes.value.length} 个区域,可继续绘制`)
}

function removeShape(i) {
  const s = drawnShapes.value[i]
  s?.overlays.forEach((o) => mapApi?.remove(o))
  drawnShapes.value.splice(i, 1)
}

/** 多区域模式:全部部件合并保存为一条围栏记录(一个开关统一启停) */
async function saveAllDrawn() {
  const shapes = drawnShapes.value
  if (!shapes.length) return ElMessage.warning('请先在地图上绘制区域')
  if (shapes.some((s) => s.shape === 'LINE' && !s.radius && !dialog.form.radius)) {
    return ElMessage.warning('线形需要走廊宽度,请先在表单中填写「走廊(m)」')
  }
  const body = {
    name: (dialog.form.name || '').trim() || '未命名围栏',
    type: dialog.form.type,
    shape: 'MULTI',
    maxAltitude: dialog.form.maxAltitude,
    remark: dialog.form.remark,
    pointsJson: JSON.stringify(shapes.map((s) => ({
      shape: s.shape,
      radius: s.shape === 'CIRCLE' ? s.radius : (s.radius ?? dialog.form.radius ?? null),
      points: s.points
    }))),
    enabled: dialog.form.enabled ?? true
  }
  drawDlg.savingAll = true
  try {
    if (dialog.form.id) await http.put(`/fences/${dialog.form.id}`, body)
    else await http.post('/fences', body)
    ElMessage.success(`已保存围栏「${body.name}」(${shapes.length} 个区域)`)
    drawDlg.visible = false
    dialog.visible = false
    fences.value = await http.get('/fences')
    redraw()
  } finally {
    drawDlg.savingAll = false
  }
}

watch(() => drawDlg.visible, (v) => {
  if (!v) {
    mapApi?.stopDraw()
    // 主图 DOM 挪回侧栏面板并触发自适应
    mapPanelRef.value?.appendChild(mapRef.value)
    requestAnimationFrame(() => window.dispatchEvent(new Event('resize')))
  }
})
</script>

<style scoped>
.fence-body {
  height: calc(100% - 50px);
  display: flex; gap: 12px;
}
.table-panel { flex: 1; padding: 8px; min-width: 0; }
.map-panel { width: 46%; overflow: hidden; }
.map-host { width: 100%; height: 100%; }
.map { width: 100%; height: 100%; }
.name { font-weight: 600; }

.ftype { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; }
.ftype i { width: 8px; height: 8px; border-radius: 2px; }
.ft-no_fly i { background: #f04438; }
.ft-limit i { background: #f79009; }
.ft-work i { background: #12b76a; }

.coord-tip { font-size: 11px; color: var(--text-dim); margin-top: 6px; line-height: 1.6; }

.draw-entry { display: flex; align-items: center; gap: 10px; }
.draw-summary { font-size: 12px; color: #98a2b3; }
.draw-summary.ok { color: #12b76a; }
.json-collapse { margin-top: 6px; border-top: none; }
.json-collapse :deep(.el-collapse-item__header) {
  font-size: 12px; color: var(--text-dim); height: 32px; line-height: 32px; border-bottom: none;
}
.json-collapse :deep(.el-collapse-item__content) { padding-bottom: 8px; }

/* —— 全屏绘制对话框 —— */
.draw-wrap { position: relative; height: calc(100vh - 55px); background: #0a1226; }
.draw-map { position: absolute; inset: 0; }
.draw-map :deep(canvas) { cursor: crosshair !important; }
.draw-toolbar {
  position: absolute; top: 14px; left: 50%; transform: translateX(-50%); z-index: 20;
  display: flex; align-items: center; gap: 12px; padding: 8px 14px;
  background: rgba(255, 255, 255, 0.96); border-radius: 12px;
  box-shadow: 0 6px 24px rgba(16, 24, 40, 0.18);
  flex-wrap: wrap; justify-content: center; max-width: 92vw;
}
.draw-shapes {
  position: absolute; top: 76px; right: 16px; z-index: 20; max-height: 55vh; overflow: auto;
  min-width: 190px; padding: 10px 12px; font-size: 12.5px; color: #344054;
  background: rgba(255, 255, 255, 0.94); border-radius: 10px;
  box-shadow: 0 4px 14px rgba(16, 24, 40, 0.15);
}
.ds-head { font-weight: 600; color: #101828; margin-bottom: 6px; }
.ds-item { display: flex; align-items: center; gap: 7px; padding: 3px 0; }
.ds-item i { width: 8px; height: 8px; border-radius: 2px; flex: none; }
.ds-text { flex: 1; white-space: nowrap; }
.draw-hint { font-size: 12px; color: #667085; white-space: nowrap; }
.draw-status {
  position: absolute; bottom: 18px; left: 50%; transform: translateX(-50%); z-index: 20;
  display: flex; gap: 16px; padding: 7px 16px; font-size: 12.5px; color: #475467;
  background: rgba(255, 255, 255, 0.92); border-radius: 10px;
  box-shadow: 0 4px 14px rgba(16, 24, 40, 0.15);
}
.draw-status b { color: #155eef; }
</style>

<style>
/* 对话框挂载在 body 下,需全局样式去掉内边距让地图铺满 */
.draw-dlg { --el-dialog-padding-primary: 14px; }
.draw-dlg .el-dialog__body { padding: 0; }
</style>
