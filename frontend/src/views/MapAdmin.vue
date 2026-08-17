<template>
  <div class="page">
    <div class="page-header">
      <span class="page-title">地图管理</span>
      <span class="page-sub">全平台底图提供商切换 · 密钥集中配置</span>
    </div>

    <!-- 提供商选择 -->
    <div class="panel providers-panel">
      <div class="panel-title">底图提供商</div>
      <div class="cards">
        <div v-for="p in providers" :key="p.id"
             class="provider-card" :class="{ active: p.id === current }"
             @click="choose(p)">
          <div class="pc-badge" :style="{ background: p.grad }">{{ p.short }}</div>
          <div class="pc-main">
            <div class="pc-name">
              <span>{{ p.name }}</span>
              <span v-if="p.id === current" class="pc-using">使用中</span>
              <span class="pc-key" :class="ready(p.id) ? 'ok' : 'no'">
                {{ ready(p.id) ? '密钥就绪' : '密钥未配置' }}
              </span>
            </div>
            <p class="pc-desc">{{ p.desc }}</p>
            <div class="pc-tags">
              <span v-for="t in p.tags" :key="t">{{ t }}</span>
            </div>
          </div>
          <div class="pc-check" :style="{ background: p.grad }">
            <el-icon color="#fff"><Check /></el-icon>
          </div>
        </div>
      </div>
      <div class="switch-tip">
        <el-icon color="#155eef"><InfoFilled /></el-icon>
        <span>切换后重新进入地图页面即生效(实时监控 / 电子围栏 / 飞行任务),航线选点仍使用百度坐标体系保证数据一致。</span>
        <el-button size="small" type="primary" plain @click="reload">立即刷新</el-button>
      </div>
    </div>

    <!-- 密钥配置 -->
    <div class="panel key-panel">
      <div class="panel-title">密钥配置</div>
      <div class="key-form">
        <div class="key-item">
          <label>高德 Key</label>
          <el-input v-model="keys.amap" placeholder="高德开放平台申请的 JS API Key" clearable />
        </div>
        <div class="key-item">
          <label>高德安全密钥</label>
          <el-input v-model="keys.amapSec" placeholder="JS API 2.0 必配的 securityJsCode" clearable show-password />
        </div>
        <div class="key-item">
          <label>天地图 Key</label>
          <el-input v-model="keys.tdt" placeholder="天地图控制台申请的浏览器端 tk" clearable />
        </div>
        <el-button type="primary" class="key-save" @click="saveKeys">保存密钥</el-button>
      </div>
      <p class="key-note">
        百度 AK 由 .env 的 VITE_BMAP_AK 提供;高德 / 天地图密钥保存在本机浏览器(localStorage),
        保存后无需重启即可切换。未配置密钥的提供商不可启用,防止地图空白。
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, InfoFilled } from '@element-plus/icons-vue'
import {
  PROVIDERS, getProviderId, setProviderId,
  getMapKeys, saveMapKeys, providerKeyReady
} from '../utils/mapProviders'

const providers = PROVIDERS
const current = ref(getProviderId())
const keys = ref({ amap: '', amapSec: '', tdt: '', ...getMapKeys() })

function ready(id) {
  return providerKeyReady(id)
}

function choose(p) {
  if (!ready(p.id)) {
    ElMessage.warning(`请先在下方配置 ${p.name} 密钥`)
    return
  }
  current.value = p.id
  setProviderId(p.id)
  ElMessage.success(`已切换为${p.name},重新进入地图页面生效`)
}

function saveKeys() {
  saveMapKeys({
    amap: keys.value.amap?.trim() || '',
    amapSec: keys.value.amapSec?.trim() || '',
    tdt: keys.value.tdt?.trim() || ''
  })
  ElMessage.success('密钥已保存到本机')
}

function reload() {
  location.reload()
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

.providers-panel, .key-panel { padding: 18px 20px; }

/* 提供商卡片 */
.cards { display: flex; flex-direction: column; gap: 12px; }

.provider-card {
  position: relative;
  display: flex;
  gap: 14px;
  padding: 16px;
  border: 1.5px solid var(--border);
  border-radius: 12px;
  cursor: pointer;
  transition: all .2s;
  background: #fff;
}
.provider-card:hover { border-color: #b8ccf7; box-shadow: var(--shadow-sm); }
.provider-card.active {
  border-color: #155eef;
  background: linear-gradient(90deg, #f5f9ff, #fff);
  box-shadow: inset 0 0 0 1px #d6e4ff;
}

.pc-badge {
  width: 46px; height: 46px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 19px; font-weight: 700;
  border-radius: 12px;
}

.pc-main { flex: 1; min-width: 0; }
.pc-name {
  display: flex; align-items: center; gap: 10px;
  font-size: 15px; font-weight: 700; color: #101828;
}
.pc-using {
  font-size: 11px; padding: 1px 9px; border-radius: 999px;
  background: #eff6ff; color: #155eef;
  border: 1px solid #d6e4ff;
}
.pc-key { font-size: 11px; padding: 1px 9px; border-radius: 999px; }
.pc-key.ok { color: #12b76a; background: #ecfdf3; border: 1px solid #d1fadf; }
.pc-key.no { color: #f04438; background: #fef3f2; border: 1px solid #fee4e2; }

.pc-desc { margin: 7px 0 0; font-size: 12.5px; color: var(--text-dim); line-height: 1.6; }

.pc-tags { display: flex; gap: 6px; margin-top: 9px; }
.pc-tags span {
  font-size: 11px; color: #475467;
  padding: 2px 9px; border-radius: 6px;
  background: #f2f4f7; border: 1px solid var(--border);
}

.pc-check {
  position: absolute; top: -8px; right: -8px;
  width: 22px; height: 22px; border-radius: 50%;
  display: none;
  align-items: center; justify-content: center;
  box-shadow: 0 2px 6px -2px rgba(21, 94, 239, .5);
}
.provider-card.active .pc-check { display: flex; }

.switch-tip {
  display: flex; align-items: center; gap: 8px;
  margin-top: 14px; padding: 10px 13px;
  border-radius: 9px;
  background: #f5f9ff;
  border: 1px solid #d6e4ff;
  font-size: 12.5px; color: #475467; line-height: 1.6;
}
.switch-tip span { flex: 1; }

/* 密钥表单 */
.key-form { display: flex; gap: 12px; align-items: flex-end; }
.key-item { flex: 1; }
.key-item label {
  display: block; margin-bottom: 6px;
  font-size: 12.5px; font-weight: 600; color: #344054;
}
.key-save { flex-shrink: 0; }

.key-note {
  margin: 14px 0 0;
  font-size: 12px; color: var(--text-dim); line-height: 1.7;
}

@media (max-width: 1100px) {
  .key-form { flex-wrap: wrap; }
  .key-item { min-width: 240px; }
}
</style>
