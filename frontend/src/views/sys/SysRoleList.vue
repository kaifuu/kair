<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">角色管理</span>
      <div class="actions">
        <el-button type="primary" @click="openDialog()">新增角色</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <el-table :data="roles" v-loading="loading" stripe height="100%">
        <el-table-column prop="name" label="角色名称" width="160">
          <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="code" label="编码" width="140">
          <template #default="{ row }">
            <el-tag size="small" :type="row.code === 'ADMIN' ? 'danger' : ''" effect="plain">{{ row.code }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="菜单权限" min-width="280">
          <template #default="{ row }">
            <span v-if="row.code === 'ADMIN'" class="admin-all">全部菜单(内置超管)</span>
            <div v-else class="menu-chips">
              <el-tag v-for="m in menusOf(row)" :key="m.id" size="small" effect="light" class="chip">{{ m.name }}</el-tag>
              <span v-if="!menusOf(row).length" class="dim">未授权</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm v-if="row.code !== 'ADMIN'" title="确认删除该角色?" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.form.id ? '编辑角色' : '新增角色'" width="620px">
      <el-form :model="dialog.form" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="角色名称" required>
              <el-input v-model="dialog.form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="角色编码" required>
              <el-input v-model="dialog.form.code" :disabled="!!dialog.form.id" placeholder="如 DISPATCHER" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="dialog.form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="菜单授权">
          <div class="menu-tree">
            <el-tree ref="treeRef" :data="menuTreeData" show-checkbox check-strictly node-key="id"
                     default-expand-all :props="{ label: 'name' }"
                     :default-checked-keys="dialog.checkedIds" />
            <div class="tree-tip">勾选该角色可见的菜单(ADMIN 角色固定拥有全部)</div>
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
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api'

const loading = ref(false)
const roles = ref([])
const menus = ref([])
const treeRef = ref(null)
const dialog = reactive({ visible: false, saving: false, form: {}, checkedIds: [] })

const menuTreeData = computed(() => ([
  { id: -1, name: '业务菜单', children: menus.value.filter((m) => m.group === 'BIZ') },
  { id: -2, name: '系统管理', children: menus.value.filter((m) => m.group === 'SYS') }
]))

function menusOf(role) {
  let ids = []
  try { ids = JSON.parse(role.menuIdsJson || '[]') } catch (e) { /* noop */ }
  return menus.value.filter((m) => ids.includes(m.id))
}

onMounted(load)
async function load() {
  loading.value = true
  try {
    ;[roles.value, menus.value] = await Promise.all([http.get('/roles'), http.get('/menus')])
  } finally { loading.value = false }
}

function openDialog(row) {
  if (row) {
    dialog.form = { id: row.id, name: row.name, code: row.code, remark: row.remark }
    let ids = []
    try { ids = JSON.parse(row.menuIdsJson || '[]') } catch (e) { /* noop */ }
    dialog.checkedIds = ids
  } else {
    dialog.form = { id: null, name: '', code: '', remark: '' }
    dialog.checkedIds = []
  }
  dialog.visible = true
  nextTick(() => treeRef.value?.setCheckedKeys(dialog.checkedIds.filter((id) => id > 0)))
}

async function save() {
  const f = dialog.form
  if (!f.name || !f.code) return ElMessage.warning('名称与编码不能为空')
  dialog.saving = true
  try {
    const ids = (treeRef.value?.getCheckedKeys() || []).filter((id) => id > 0)
    const body = { ...f, menuIdsJson: JSON.stringify(ids) }
    if (f.id) await http.put(`/roles/${f.id}`, body)
    else await http.post('/roles', body)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function remove(id) {
  await http.delete(`/roles/${id}`)
  ElMessage.success('已删除')
  load()
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px); padding: 8px; }
.name { font-weight: 600; }
.admin-all { color: #d92d20; font-weight: 600; font-size: 13px; }
.menu-chips { display: flex; flex-wrap: wrap; gap: 4px; }
.dim { color: #98a2b3; font-size: 12px; }
.menu-tree { width: 100%; border: 1px solid var(--border); border-radius: 8px; padding: 8px; }
.tree-tip { font-size: 12px; color: var(--text-dim); margin-top: 6px; }
</style>
