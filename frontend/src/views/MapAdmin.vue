<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">地图管理</span>
      <span class="page-sub">底图厂商配置 · 凭证/自定义瓦片集中维护 · 默认底图设置</span>
    </div>

    <div class="panel">
      <div class="panel-title row-between">
        <span>底图厂商配置</span>
        <el-button type="primary" size="small" :icon="Plus" @click="openCreate">新增配置</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" row-key="id">
        <el-table-column label="底图" min-width="200">
          <template #default="{ row }">
            <div class="prov-cell">
              <span class="prov-badge" :style="{ background: row.grad || gradOf(row.vendor) }">
                {{ (row.name || row.code).trim().charAt(0) }}
              </span>
              <div class="prov-meta">
                <div class="prov-name">
                  {{ row.name }}
                  <span v-if="row.code === current" class="tag tag-use">使用中</span>
                  <span v-if="row.isDefault" class="tag tag-def">默认</span>
                </div>
                <div class="prov-code">{{ row.code }} · {{ vendorInfo(row.vendor).label }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="接入凭证" min-width="130">
          <template #default="{ row }">
            <span class="tag" :class="credsReady(row) ? 'tag-ok' : 'tag-no'">
              {{ credsReady(row) ? '已配置' : '未配置' }}
            </span>
            <div v-if="!credsReady(row) && fallbackReady(row)" class="fb-hint">环境变量兜底可用</div>
          </template>
        </el-table-column>
        <el-table-column label="自定义瓦片" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.vendor === 'CUSTOM'">{{ row.tileUrl || '—' }}</span>
            <span v-else class="dim">内置厂商</span>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :loading="row._saving" @change="toggleEnabled(row)" />
          </template>
        </el-table-column>
        <el-table-column label="排序" prop="sort" width="70" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="use(row)" :disabled="!canUse(row)">使用</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" size="small" :disabled="row.isDefault || !row.enabled"
                       @click="setDefault(row)">设为默认</el-button>
            <el-button v-if="row.vendor === 'CUSTOM'" link type="danger" size="small"
                       @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="switch-tip">
        <el-icon color="#155eef"><InfoFilled /></el-icon>
        <span>
          「默认」为全平台未本机选择时的缺省底图;「使用」仅本机立即切换,重新进入地图页面生效。
          内置厂商(百度/高德/天地图)不可删除,可停用或清空凭证;凭证取值链:本页厂商配置 → 本机 localStorage → .env / yml。
        </span>
      </div>
    </div>

    <!-- 新增/编辑配置 -->
    <el-dialog v-model="dialog.visible" width="620px" :title="dialog.form.id ? '编辑底图配置' : '新增底图配置'" destroy-on-close>
      <el-form :model="dialog.form" label-width="100px">
        <el-form-item label="厂商" required>
          <el-radio-group v-model="dialog.form.vendor" :disabled="!!dialog.form.id">
            <el-radio v-for="v in VENDORS" :key="v.code" :value="v.code">{{ v.label }}</el-radio>
          </el-radio-group>
          <div v-if="!dialog.form.id" class="field-tip">厂商创建后不可变更;自定义厂商需提供 XYZ 瓦片地址与渲染引擎。</div>
          <div v-else class="field-tip">厂商不可修改,如需变更请新建配置。</div>
        </el-form-item>
        <el-form-item label="标识" required>
          <el-input v-model="dialog.form.code" :disabled="!!dialog.form.id" maxlength="32"
                    placeholder="如 survey-tile,仅字母/数字/下划线/中划线" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="dialog.form.name" maxlength="64" placeholder="如:内网影像底图" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="dialog.form.description" maxlength="200" placeholder="可选" />
        </el-form-item>

        <el-form-item v-for="c in activeCreds" :key="c.key" :label="c.label" :required="!vendorInfo(dialog.form.vendor).custom">
          <el-input v-model="dialog.form.creds[c.key]" :placeholder="c.ph" show-password clearable />
        </el-form-item>
        <el-form-item v-if="vendorInfo(dialog.form.vendor).custom" label=" ">
          <div class="field-tip">凭证保存在服务端,对全平台登录用户生效;清空保存即清除(自定义瓦片密钥可为空)。</div>
        </el-form-item>

        <template v-if="vendorInfo(dialog.form.vendor).custom">
          <el-form-item label="渲染引擎" required>
            <el-radio-group v-model="dialog.form.engine">
              <el-radio value="tdt">天地图(推荐)</el-radio>
              <el-radio value="amap">高德</el-radio>
              <el-radio value="baidu">百度</el-radio>
            </el-radio-group>
            <div class="field-tip">
              大多数 XYZ 瓦片服务(OSM / MapBox / 内网瓦片)为 WGS-84 坐标,选天地图引擎叠加无偏移;
              高德(GCJ-02)/ 百度(BD-09)引擎会偏移数百米,且百度瓦片切片方案与通用 XYZ 不同,通常不建议。
            </div>
          </el-form-item>
          <el-form-item label="瓦片 URL" required>
            <el-input v-model="dialog.form.tileUrl" placeholder="https://tile.example.com/{z}/{x}/{y}.png" />
            <div class="field-tip">须含 {z} {x} {y} 占位符;需要密钥的服务可加 {key} 占位符,由上方瓦片密钥替换。</div>
          </el-form-item>
        </template>

        <el-form-item label="标识配色">
          <div class="grad-row">
            <el-color-picker v-model="dialog.form.c1" />
            <el-color-picker v-model="dialog.form.c2" />
            <div class="grad-preview" :style="{ background: previewGrad }">{{ shortPreview }}</div>
          </div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dialog.form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="dialog.form.enabled" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { InfoFilled, Plus } from '@element-plus/icons-vue'
import {
  VENDORS, vendorInfo, getProviderId, setProviderId, providerKeyReady,
  ensureServerProviders, fetchProviderRows, saveProviderRow, deleteProviderRow, setDefaultProviderRow
} from '../utils/mapProviders'

const loading = ref(false)
const rows = ref([])
const current = ref(getProviderId())

const dialog = reactive({ visible: false, saving: false, form: { creds: {} } })

const activeCreds = computed(() => vendorInfo(dialog.form.vendor).creds || [])
const previewGrad = computed(() =>
  `linear-gradient(135deg,${dialog.form.c1 || '#6366f1'},${dialog.form.c2 || '#22d3ee'})`)
const shortPreview = computed(() => (dialog.form.name || '').trim().charAt(0) || '图')

onMounted(load)

async function load() {
  loading.value = true
  try {
    await ensureServerProviders(true)    // 预热注册表缓存(使用/就绪判定依赖)
    rows.value = await fetchProviderRows()
    current.value = getProviderId()
  } finally {
    loading.value = false
  }
}

/* ---------- 表格辅助 ---------- */

function gradOf(vendor) {
  return { BAIDU: 'linear-gradient(135deg,#337cff,#00c8ff)', AMAP: 'linear-gradient(135deg,#00b96b,#7be6b0)',
    TDT: 'linear-gradient(135deg,#1a7f6e,#8fd26b)', CUSTOM: 'linear-gradient(135deg,#6366f1,#a855f7)' }[vendor]
}

function rowCreds(row) {
  try {
    return JSON.parse(row.credentialsJson || '{}')
  } catch (e) {
    return {}
  }
}

/** 服务端凭证是否已配置(按厂商必填字段) */
function credsReady(row) {
  const info = vendorInfo(row.vendor)
  const creds = rowCreds(row)
  if (info.custom) return true                            // 自定义瓦片密钥可空
  return info.creds.every((c) => (creds[c.key] || '').trim())
}

/** 自身未配置时,引擎密钥是否有本机/环境变量兜底 */
function fallbackReady(row) {
  return providerKeyReady(row.code)
}

function canUse(row) {
  return row.enabled && providerKeyReady(row.code)
}

/* ---------- 行操作 ---------- */

function use(row) {
  if (!canUse(row)) {
    ElMessage.warning(`请先配置 ${row.name} 所需凭证`)
    return
  }
  setProviderId(row.code)
  current.value = row.code
  ElMessage.success(`已切换为${row.name},重新进入地图页面生效`)
}

async function toggleEnabled(row) {
  row._saving = true
  try {
    await saveProviderRow({ id: row.id, body: { enabled: row.enabled } })
    ElMessage.success(row.enabled ? '已启用' : '已停用')
    await load()
  } catch (e) {
    row.enabled = !row.enabled
  } finally {
    row._saving = false
  }
}

async function setDefault(row) {
  await setDefaultProviderRow(row.id)
  ElMessage.success(`已将「${row.name}」设为平台默认底图`)
  await load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除配置「${row.name}」?`, '删除确认', { type: 'warning' })
  try {
    await deleteProviderRow(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    /* 后端守卫(默认/内置不可删)报错由拦截器提示 */
  }
}

/* ---------- 新增/编辑 ---------- */

function openCreate() {
  dialog.form = {
    id: null, code: '', vendor: 'BAIDU', name: '', description: '',
    creds: {}, engine: 'tdt', tileUrl: '', c1: '#337cff', c2: '#00c8ff',
    sort: (rows.value.length + 1) * 10, enabled: true
  }
  dialog.visible = true
}

function openEdit(row) {
  const creds = rowCreds(row)
  const hexes = (row.grad || '').match(/#[0-9a-fA-F]{3,8}/g) || []
  dialog.form = {
    id: row.id,
    code: row.code,
    vendor: row.vendor,
    name: row.name,
    description: row.description || '',
    creds: { ...creds },
    engine: row.engine || 'tdt',
    tileUrl: row.tileUrl || '',
    c1: hexes[0] || '#6366f1',
    c2: hexes[1] || '#22d3ee',
    sort: row.sort ?? 0,
    enabled: row.enabled !== false
  }
  dialog.visible = true
}

async function save() {
  const f = dialog.form
  const info = vendorInfo(f.vendor)
  if (!f.name?.trim()) return ElMessage.warning('请输入名称')
  if (!f.id) {
    if (!/^[a-zA-Z0-9_-]{2,32}$/.test(f.code || '')) {
      return ElMessage.warning('标识仅允许字母/数字/下划线/中划线,2-32 位')
    }
  }
  if (info.custom) {
    const url = f.tileUrl?.trim() || ''
    if (!/^https?:\/\//.test(url)) return ElMessage.warning('瓦片 URL 需以 http(s):// 开头')
    for (const t of ['{z}', '{x}', '{y}']) {
      if (!url.includes(t)) return ElMessage.warning(`瓦片 URL 缺少占位符 ${t}`)
    }
  }
  dialog.saving = true
  try {
    const creds = {}
    for (const c of info.creds) {
      const v = (f.creds[c.key] || '').trim()
      if (v || !info.custom) creds[c.key] = v       // 内置厂商保留字段(可为空=清除);自定义空则不存
    }
    await saveProviderRow({
      id: f.id,
      body: {
        code: f.code?.trim(),
        vendor: f.vendor,
        name: f.name.trim(),
        description: f.description?.trim() || '',
        credentialsJson: JSON.stringify(creds),
        tileUrl: info.custom ? f.tileUrl.trim() : null,
        engine: info.custom ? f.engine : null,
        grad: `linear-gradient(135deg,${f.c1 || '#6366f1'},${f.c2 || '#22d3ee'})`,
        sort: f.sort ?? 0,
        enabled: f.enabled !== false
      }
    })
    ElMessage.success('已保存' + (f.code === current.value ? ',重新进入地图页面生效' : ''))
    dialog.visible = false
    await load()
  } finally {
    dialog.saving = false
  }
}
</script>

<style scoped>
.page {
  height: 100%;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.page-sub { font-size: 13px; color: var(--text-dim); }
.panel { padding: 18px 20px; }
.row-between { display: flex; align-items: center; justify-content: space-between; }

/* 表格内厂商单元格 */
.prov-cell { display: flex; align-items: center; gap: 10px; }
.prov-badge {
  width: 38px; height: 38px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 16px; font-weight: 700; border-radius: 10px;
}
.prov-name {
  display: flex; align-items: center; gap: 7px;
  font-size: 14px; font-weight: 700; color: #101828;
}
.prov-code { font-size: 12px; color: var(--text-dim); margin-top: 2px; }

.tag {
  font-size: 11px; padding: 1px 9px; border-radius: 999px; white-space: nowrap;
}
.tag-use { background: #eff6ff; color: #155eef; border: 1px solid #d6e4ff; }
.tag-def { background: #ecfdf3; color: #12b76a; border: 1px solid #d1fadf; }
.tag-ok { color: #12b76a; background: #ecfdf3; border: 1px solid #d1fadf; }
.tag-no { color: #f04438; background: #fef3f2; border: 1px solid #fee4e2; }
.fb-hint { font-size: 11px; color: var(--text-dim); margin-top: 3px; }
.dim { color: var(--text-dim); }

.switch-tip {
  display: flex; align-items: center; gap: 8px;
  margin-top: 14px; padding: 10px 13px;
  border-radius: 9px;
  background: #f5f9ff;
  border: 1px solid #d6e4ff;
  font-size: 12.5px; color: #475467; line-height: 1.6;
}
.switch-tip span { flex: 1; }

/* 弹窗表单 */
.field-tip {
  width: 100%;
  margin-top: 6px;
  font-size: 12px; color: var(--text-dim); line-height: 1.7;
}
.grad-row { display: flex; align-items: center; gap: 12px; }
.grad-preview {
  width: 34px; height: 34px; border-radius: 9px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 15px; font-weight: 700;
}
</style>
