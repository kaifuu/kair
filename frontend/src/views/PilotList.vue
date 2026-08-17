<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">飞手管理</span>
      <div class="actions">
        <el-input v-model="keyword" placeholder="搜索姓名/执照号/单位" clearable style="width: 240px" :prefix-icon="Search" />
        <el-button type="primary" @click="openDialog()">新增飞手</el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <el-table :data="filtered" v-loading="loading" stripe height="100%">
        <el-table-column prop="name" label="姓名" width="90">
          <template #default="{ row }"><span class="name">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="licenseNo" label="执照编号" width="130">
          <template #default="{ row }"><span class="code">{{ row.licenseNo }}</span></template>
        </el-table-column>
        <el-table-column prop="phone" label="联系电话" width="130" />
        <el-table-column prop="org" label="所属单位" min-width="140" />
        <el-table-column prop="licenseType" label="执照类型" width="130" />
        <el-table-column label="等级" width="70">
          <template #default="{ row }">{{ row.licenseGrade ? 'IV类' : '-' }}</template>
        </el-table-column>
        <el-table-column label="有效期至" width="110">
          <template #default="{ row }">
            <span :class="{ expired: isExpired(row) }">{{ row.licenseExpiry }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <span class="status" :class="'ps-' + row.status.toLowerCase()"><i></i>{{ statusText[row.status] }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="totalFlightHours" label="飞行时长(h)" width="110" sortable />
        <el-table-column prop="totalFlights" label="架次" width="80" sortable />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除该飞手?" @confirm="remove(row.id)">
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.form.id ? '编辑飞手' : '新增飞手'" width="560px">
      <el-form :model="dialog.form" label-width="92px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="姓名" required>
              <el-input v-model="dialog.form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="执照编号" required>
              <el-input v-model="dialog.form.licenseNo" :disabled="!!dialog.form.id" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="dialog.form.phone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属单位">
              <el-input v-model="dialog.form.org" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="执照类型">
              <el-select v-model="dialog.form.licenseType" style="width: 100%">
                <el-option v-for="t in licenseTypes" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期至">
              <el-date-picker v-model="dialog.form.licenseExpiry" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="等级">
              <el-select v-model="dialog.form.licenseGrade" style="width: 100%">
                <el-option label="IV 类" :value="4" />
                <el-option label="III 类" :value="3" />
                <el-option label="II 类" :value="2" />
                <el-option label="I 类" :value="1" />
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

const statusText = { ACTIVE: '正常', SUSPENDED: '暂停', EXPIRED: '过期' }
const licenseTypes = ['多旋翼', '固定翼', '直升机', '垂直起降固定翼']

const loading = ref(false)
const keyword = ref('')
const pilots = ref([])

const dialog = reactive({ visible: false, saving: false, form: {} })

const filtered = computed(() => pilots.value.filter((p) =>
  !keyword.value || p.name.includes(keyword.value) || p.licenseNo.includes(keyword.value) || (p.org || '').includes(keyword.value)
))

const isExpired = (row) => row.licenseExpiry && row.licenseExpiry < new Date().toISOString().slice(0, 10)

onMounted(load)
async function load() {
  loading.value = true
  try { pilots.value = await http.get('/pilots') } finally { loading.value = false }
}

function openDialog(row) {
  dialog.form = row ? { ...row } : { name: '', licenseNo: '', phone: '', org: '', licenseType: '多旋翼', licenseGrade: 4, status: 'ACTIVE' }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  if (!f.name || !f.licenseNo) return ElMessage.warning('请填写姓名与执照编号')
  dialog.saving = true
  try {
    if (f.id) await http.put(`/pilots/${f.id}`, f)
    else await http.post('/pilots', f)
    ElMessage.success('保存成功')
    dialog.visible = false
    load()
  } finally { dialog.saving = false }
}

async function remove(id) {
  await http.delete(`/pilots/${id}`)
  ElMessage.success('已删除')
  load()
}
</script>

<style scoped>
.table-panel { height: calc(100% - 50px); padding: 8px; }
.actions { display: flex; gap: 10px; }
.name { font-weight: 600; }
.code { color: var(--primary); font-size: 13px; }
.expired { color: var(--danger); }

.status { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; }
.status i { width: 7px; height: 7px; border-radius: 50%; }
.ps-active i { background: #12b76a; box-shadow: 0 0 6px #12b76a; }
.ps-suspended i { background: #f79009; }
.ps-expired i { background: #f04438; }
</style>
