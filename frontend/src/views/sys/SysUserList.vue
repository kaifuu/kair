<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">人员管理</span>
      <div class="actions">
        <el-input v-model="keyword" placeholder="账号 / 姓名 / 手机号" clearable style="width: 220px" @keyup.enter="load">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="openDialog()">新增人员</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <el-table :data="filtered" v-loading="loading" stripe height="100%">
        <el-table-column prop="username" label="账号" width="130">
          <template #default="{ row }"><span class="name">{{ row.username }}</span></template>
        </el-table-column>
        <el-table-column prop="nickname" label="姓名" width="110" />
        <el-table-column prop="phone" label="手机号" width="130">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="row.role?.code === 'ADMIN' ? 'danger' : 'primary'" effect="light">
              {{ row.role?.name || '未分配' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所属组织" width="150">
          <template #default="{ row }">{{ orgName(row.orgId) || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'ENABLED' ? 'success' : 'info'" effect="plain">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近登录" width="150">
          <template #default="{ row }">{{ fmt(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm :title="`重置密码为 ${DEFAULT_PWD} ?`" @confirm="resetPwd(row)">
              <template #reference>
                <el-button link type="warning" size="small">重置密码</el-button>
              </template>
            </el-popconfirm>
            <el-popconfirm v-if="row.username !== 'admin'" title="确认删除该人员?" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.form.id ? '编辑人员' : '新增人员'" width="560px">
      <el-form :model="dialog.form" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="账号" required>
              <el-input v-model="dialog.form.username" :disabled="!!dialog.form.id" placeholder="登录账号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名">
              <el-input v-model="dialog.form.nickname" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="手机号">
              <el-input v-model="dialog.form.phone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色">
              <el-select v-model="dialog.form.roleId" clearable style="width: 100%">
                <el-option v-for="r in roles" :key="r.id" :label="r.name + '(' + r.code + ')'" :value="r.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="所属组织">
              <el-tree-select v-model="dialog.form.orgId" :data="orgTree" clearable check-strictly
                              :props="{ label: 'name', value: 'id' }" node-key="id" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="dialog.form.enabled" active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert v-if="!dialog.form.id" :title="`初始密码为 ${DEFAULT_PWD},请登录后尽快修改`" type="info" :closable="false" />
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
import http from '../../api'

const DEFAULT_PWD = '123456'

const loading = ref(false)
const keyword = ref('')
const users = ref([])
const roles = ref([])
const orgTree = ref([])
const orgFlat = ref([])
const dialog = reactive({ visible: false, saving: false, form: {} })

const filtered = computed(() => users.value.filter((u) => !keyword.value ||
  (u.username || '').includes(keyword.value) || (u.nickname || '').includes(keyword.value) || (u.phone || '').includes(keyword.value)))

onMounted(load)
async function load() {
  loading.value = true
  try {
    ;[users.value, roles.value, orgTree.value] = await Promise.all([
      http.get('/users'), http.get('/roles'), http.get('/orgs')
    ])
    orgFlat.value = flatten(orgTree.value)
  } finally { loading.value = false }
}

function flatten(nodes, out = []) {
  nodes.forEach((n) => { out.push(n); flatten(n.children || [], out) })
  return out
}

function orgName(id) {
  return orgFlat.value.find((o) => o.id === id)?.name
}

function fmt(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

function openDialog(row) {
  dialog.form = row
    ? { id: row.id, username: row.username, nickname: row.nickname, phone: row.phone,
        roleId: row.role?.id || null, orgId: row.orgId || null, enabled: row.status === 'ENABLED' }
    : { id: null, username: '', nickname: '', phone: '', roleId: null, orgId: null, enabled: true }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.username) return ElMessage.warning('账号不能为空')
  dialog.saving = true
  try {
    const body = {
      username: f.username, nickname: f.nickname, phone: f.phone,
      role: f.roleId ? { id: f.roleId } : null, orgId: f.orgId,
      status: f.enabled ? 'ENABLED' : 'DISABLED'
    }
    if (f.id) await http.put(`/users/${f.id}`, body)
    else await http.post('/users', body)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function resetPwd(row) {
  await http.post(`/users/${row.id}/reset-password`)
  ElMessage.success(`密码已重置为 ${DEFAULT_PWD}`)
}

async function remove(id) {
  await http.delete(`/users/${id}`)
  ElMessage.success('已删除')
  load()
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px); padding: 8px; }
.actions { display: flex; gap: 10px; align-items: center; }
.name { font-weight: 600; }
</style>
