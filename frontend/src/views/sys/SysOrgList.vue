<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">组织管理</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog(null)">新增组织</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <el-table :data="orgs" v-loading="loading" row-key="id" default-expand-all
                :tree-props="{ children: 'children' }" stripe height="100%">
        <el-table-column prop="name" label="组织名称" min-width="240">
          <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="orgCode" label="组织编码" width="140">
          <template #default="{ row }"><span class="code">{{ row.orgCode || '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="success" size="small" @click="openDialog(null, row.id)">加子组织</el-button>
            <el-popconfirm title="确认删除该组织?" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.form.id ? '编辑组织' : '新增组织'" width="520px">
      <el-form :model="dialog.form" label-width="90px">
        <el-form-item label="组织名称" required>
          <el-input v-model="dialog.form.name" />
        </el-form-item>
        <el-form-item label="上级组织">
          <el-tree-select v-model="dialog.form.parentId" :data="orgTree" clearable check-strictly
                          :props="{ label: 'name', value: 'id' }" node-key="id" style="width: 100%"
                          placeholder="留空为顶级组织" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="组织编码">
              <el-input v-model="dialog.form.orgCode" placeholder="如 WRJ-04" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="dialog.form.sort" :min="1" :max="99" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
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
const orgTree = ref([])
const orgs = ref([])
const dialog = reactive({ visible: false, saving: false, form: {} })

onMounted(load)
async function load() {
  loading.value = true
  try { orgTree.value = await http.get('/orgs'); orgs.value = orgTree.value }
  finally { loading.value = false }
}

function openDialog(row, parentId = null) {
  dialog.form = row
    ? { id: row.id, name: row.name, parentId: row.parentId, orgCode: row.orgCode, sort: row.sort, enabled: row.enabled }
    : { id: null, name: '', parentId, orgCode: '', sort: 9, enabled: true }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.name) return ElMessage.warning('组织名称不能为空')
  dialog.saving = true
  try {
    if (f.id) await http.put(`/orgs/${f.id}`, f)
    else await http.post('/orgs', f)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function remove(id) {
  await http.delete(`/orgs/${id}`)
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
