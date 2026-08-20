<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">租户管理</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">新增租户</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <el-table :data="tenants" v-loading="loading" stripe height="100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="name" label="租户名称" width="200">
          <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="code" label="租户编码" width="140">
          <template #default="{ row }"><span class="code">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该租户?" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.form.id ? '编辑租户' : '新增租户'" width="520px">
      <el-form :model="dialog.form" label-width="90px">
        <el-form-item label="租户名称" required>
          <el-input v-model="dialog.form.name" />
        </el-form-item>
        <el-form-item label="租户编码" required>
          <el-input v-model="dialog.form.code" :disabled="!!dialog.form.id" placeholder="如 PARK-01" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dialog.form.remark" type="textarea" :rows="2" />
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

const loading = ref(false)
const tenants = ref([])
const dialog = reactive({ visible: false, saving: false, form: {} })

onMounted(load)
async function load() {
  loading.value = true
  try { tenants.value = await http.get('/tenants') }
  finally { loading.value = false }
}

function openDialog(row) {
  dialog.form = row
    ? { id: row.id, name: row.name, code: row.code, remark: row.remark, enabled: row.enabled }
    : { id: null, name: '', code: '', remark: '', enabled: true }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.name || !f.code) return ElMessage.warning('名称与编码不能为空')
  dialog.saving = true
  try {
    if (f.id) await http.put(`/tenants/${f.id}`, f)
    else await http.post('/tenants', f)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function remove(id) {
  await http.delete(`/tenants/${id}`)
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
