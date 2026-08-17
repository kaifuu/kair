<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">无人机管理</span>
      <div class="actions">
        <el-input v-model="keyword" placeholder="搜索编号/机型" clearable style="width: 220px" :prefix-icon="Search" />
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="(v, k) in statusText" :key="k" :label="v" :value="k" />
        </el-select>
        <el-button type="primary" @click="openDialog()">新增无人机</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <el-table :data="filtered" v-loading="loading" stripe height="100%">
        <el-table-column prop="code" label="机身编号" width="150">
          <template #default="{ row }"><span class="code">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column prop="model" label="机型" width="160" />
        <el-table-column prop="manufacturer" label="制造商" width="110" />
        <el-table-column prop="category" label="用途" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <span class="status" :class="'st-' + row.status.toLowerCase()">
              <i></i>{{ statusText[row.status] }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="绑定飞手" width="100">
          <template #default="{ row }">{{ row.pilot?.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="归航点" min-width="180">
          <template #default="{ row }">
            {{ row.homeLng ? row.homeLng.toFixed(4) + ', ' + row.homeLat.toFixed(4) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="totalFlightHours" label="累计时长(h)" width="110" sortable />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该无人机?" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.form.id ? '编辑无人机' : '新增无人机'" width="560px">
      <el-form :model="dialog.form" label-width="92px">
        <el-form-item label="机身编号" required>
          <el-input v-model="dialog.form.code" placeholder="如 UAV-2026-0007" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="机型">
              <el-input v-model="dialog.form.model" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="制造商">
              <el-input v-model="dialog.form.manufacturer" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="用途">
              <el-select v-model="dialog.form.category" style="width: 100%">
                <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="dialog.form.status" style="width: 100%">
                <el-option v-for="(v, k) in statusText" :key="k" :label="v" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="绑定飞手">
          <el-select v-model="dialog.form.pilotId" clearable placeholder="不绑定" style="width: 100%">
            <el-option v-for="p in pilots" :key="p.id" :label="p.name + ' · ' + p.licenseNo" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="归航点">
          <div class="coord-row">
            <el-input v-model.number="dialog.form.homeLng" placeholder="经度" />
            <el-input v-model.number="dialog.form.homeLat" placeholder="纬度" />
          </div>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import http from '../api'

const statusText = { IDLE: '待命', FLYING: '飞行中', CHARGING: '充电中', MAINTENANCE: '维保中', OFFLINE: '离线' }
const categories = ['巡检', '航拍', '测绘', '物流', '农业', '警用']

const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')
const drones = ref([])
const pilots = ref([])

const dialog = reactive({
  visible: false, saving: false,
  form: {}
})

const filtered = computed(() => drones.value.filter((d) =>
  (!keyword.value || d.code.includes(keyword.value) || (d.model || '').includes(keyword.value)) &&
  (!statusFilter.value || d.status === statusFilter.value)
))

onMounted(load)

async function load() {
  loading.value = true
  try {
    ;[drones.value, pilots.value] = await Promise.all([http.get('/drones'), http.get('/pilots')])
  } finally { loading.value = false }
}

function openDialog(row) {
  dialog.form = row
    ? { ...row, pilotId: row.pilot?.id }
    : { code: '', model: '', manufacturer: '', category: '巡检', status: 'IDLE', homeLng: 116.4, homeLat: 39.9 }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.code) return ElMessage.warning('请输入机身编号')
  dialog.saving = true
  try {
    const body = { ...f, pilot: f.pilotId ? { id: f.pilotId } : null }
    if (f.id) await http.put(`/drones/${f.id}`, body)
    else await http.post('/drones', body)
    ElMessage.success('保存成功')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function remove(id) {
  await http.delete(`/drones/${id}`)
  ElMessage.success('已删除')
  load()
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px); padding: 8px; }
.actions { display: flex; gap: 10px; }
.code { color: var(--primary); font-weight: 600; }
.coord-row { display: flex; gap: 8px; width: 100%; }

.status { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; }
.status i { width: 7px; height: 7px; border-radius: 50%; }
.st-idle i { background: #98a2b3; }
.st-flying i { background: #155eef; box-shadow: 0 0 6px #155eef; }
.st-charging i { background: #f79009; }
.st-maintenance i { background: #f04438; }
.st-offline i { background: #cdd5e1; }
</style>
