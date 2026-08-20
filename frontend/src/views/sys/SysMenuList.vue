<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">菜单管理</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">新增菜单</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <el-table :data="menus" v-loading="loading" stripe height="100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="图标" width="64">
          <template #default="{ row }">
            <el-icon :size="18" color="#475467"><component :is="row.icon" /></el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="菜单名称" width="150">
          <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" width="150">
          <template #default="{ row }"><span class="code">{{ row.path }}</span></template>
        </el-table-column>
        <el-table-column prop="icon" label="图标名" width="140" />
        <el-table-column label="分组" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.group === 'BIZ' ? 'primary' : 'warning'" effect="light">
              {{ row.group === 'BIZ' ? '业务菜单' : '系统管理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该菜单?" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.form.id ? '编辑菜单' : '新增菜单'" width="540px">
      <el-form :model="dialog.form" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="菜单名称" required>
              <el-input v-model="dialog.form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="路由路径" required>
              <el-input v-model="dialog.form.path" placeholder="/sys/xxx" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="分组">
              <el-select v-model="dialog.form.group" style="width: 100%">
                <el-option label="业务菜单" value="BIZ" />
                <el-option label="系统管理" value="SYS" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="dialog.form.sort" :min="1" :max="99" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="图标">
          <el-select v-model="dialog.form.icon" filterable style="width: 100%">
            <el-option v-for="ic in ICONS" :key="ic" :label="ic" :value="ic">
              <span style="display:inline-flex;align-items:center;gap:8px">
                <el-icon><component :is="ic" /></el-icon>{{ ic }}
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="dialog.form.enabled" active-text="启用" inactive-text="停用" />
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api'

// 常用图标白名单(全部图标已在 main.js 全局注册)
const ICONS = ['Monitor', 'Cpu', 'User', 'Aim', 'Location', 'Bell', 'TrendCharts', 'MapLocation',
  'Connection', 'UserFilled', 'Key', 'Menu', 'OfficeBuilding', 'Files', 'Document',
  'Setting', 'DataAnalysis', 'VideoCamera', 'Compass', 'Grid']

const loading = ref(false)
const menus = ref([])
const dialog = reactive({ visible: false, saving: false, form: {} })

onMounted(load)
async function load() {
  loading.value = true
  try { menus.value = await http.get('/menus') }
  finally { loading.value = false }
}

function openDialog(row) {
  dialog.form = row
    ? { id: row.id, name: row.name, path: row.path, icon: row.icon, group: row.group, sort: row.sort, enabled: row.enabled }
    : { id: null, name: '', path: '', icon: 'Menu', group: 'BIZ', sort: 99, enabled: true }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.name || !f.path) return ElMessage.warning('名称与路径不能为空')
  dialog.saving = true
  try {
    if (f.id) await http.put(`/menus/${f.id}`, f)
    else await http.post('/menus', f)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function remove(id) {
  await http.delete(`/menus/${id}`)
  ElMessage.success('已删除')
  load()
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px); padding: 8px; }
.actions { display: flex; gap: 10px; }
.name { font-weight: 600; }
.code { font-family: monospace; font-size: 13px; color: var(--primary); }
</style>
