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
          <el-table-column label="形状" width="80">
            <template #default="{ row }">{{ row.shape === 'CIRCLE' ? '圆形' : '多边形' }}</template>
          </el-table-column>
          <el-table-column label="限高(m)" width="90">
            <template #default="{ row }">{{ row.type === 'NO_FLY' ? '-' : row.maxAltitude }}</template>
          </el-table-column>
          <el-table-column label="半径(m)" width="90">
            <template #default="{ row }">{{ row.shape === 'CIRCLE' ? row.radius : '-' }}</template>
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
        <div ref="mapRef" class="map"></div>
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
                <el-option label="圆形" value="CIRCLE" />
                <el-option label="多边形" value="POLYGON" />
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
            <el-form-item label="半径(m)" v-if="dialog.form.shape === 'CIRCLE'">
              <el-input-number v-model="dialog.form.radius" :min="100" :max="50000" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="坐标点">
          <div class="coord-editor">
            <el-input v-model="dialog.form.pointsJson" type="textarea" :rows="4" :placeholder="pointsPlaceholder" />
            <div class="coord-tip">JSON 数组:{"lng":经度,"lat":纬度};多边形至少 3 点自动闭合,圆形 1 个中心点</div>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../api'
import { createMap } from '../utils/mapAdapter'

const typeText = { NO_FLY: '禁飞区', LIMIT: '限飞区', WORK: '作业区' }
const colors = { NO_FLY: '#f04438', LIMIT: '#f79009', WORK: '#12b76a' }

const pointsPlaceholder = `[{"lng":116.397,"lat":39.910}]`

const loading = ref(false)
const fences = ref([])
const mapRef = ref(null)

const dialog = reactive({ visible: false, saving: false, form: {} })

let mapApi = null
let overlays = []

onMounted(async () => {
  fences.value = await http.get('/fences')
  try {
    mapApi = await createMap(mapRef.value, { center: { lng: 116.404, lat: 39.925 }, zoom: 10, customStyle: true })
  } catch (e) {
    ElMessage.error(e.message || '地图加载失败,请到「地图管理」检查密钥配置')
    return
  }
  redraw()
})

onUnmounted(() => mapApi?.destroy())

function redraw() {
  if (!mapApi) return
  overlays.forEach((o) => mapApi.remove(o))
  overlays = []
  fences.value.forEach((f) => {
    if (!f.enabled) return
    let pts = []
    try { pts = JSON.parse(f.pointsJson || '[]') } catch (e) { return }
    if (!pts.length) return
    const color = colors[f.type]
    let shape
    if (f.shape === 'CIRCLE' && f.radius) {
      shape = mapApi.addCircle(pts[0], f.radius, { color, dashed: true, fillOpacity: 0.14, opacity: 0.9 })
    } else if (pts.length >= 3) {
      shape = mapApi.addPolygon(pts, { color, fillOpacity: 0.14, opacity: 0.9 })
    } else return
    overlays.push(shape)
  })
}

function onRowClick(row) {
  if (!row || !mapApi) return
  let pts = []
  try { pts = JSON.parse(row.pointsJson || '[]') } catch (e) { return }
  if (row.shape === 'CIRCLE' && pts[0]) {
    mapApi.flyTo(pts[0], 12)
  } else if (pts.length) {
    mapApi.setViewport(pts)
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
  try { JSON.parse(f.pointsJson || '[]') } catch (e) {
    return ElMessage.error('坐标点 JSON 格式错误')
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
</script>

<style scoped>
.fence-body {
  height: calc(100% - 50px);
  display: flex; gap: 12px;
}
.table-panel { flex: 1; padding: 8px; min-width: 0; }
.map-panel { width: 46%; overflow: hidden; }
.map { width: 100%; height: 100%; }
.name { font-weight: 600; }

.ftype { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; }
.ftype i { width: 8px; height: 8px; border-radius: 2px; }
.ft-no_fly i { background: #f04438; }
.ft-limit i { background: #f79009; }
.ft-work i { background: #12b76a; }

.coord-tip { font-size: 11px; color: var(--text-dim); margin-top: 6px; line-height: 1.6; }
</style>
