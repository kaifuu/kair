<template>
  <el-container class="layout">
    <!-- 侧边栏 -->
    <el-aside :width="collapsed ? '64px' : '216px'" class="aside">
      <div class="logo" @click="$router.push('/monitor')">
        <div class="logo-icon">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.6">
            <circle cx="5" cy="5" r="2.6"/><circle cx="19" cy="5" r="2.6"/>
            <circle cx="5" cy="19" r="2.6"/><circle cx="19" cy="19" r="2.6"/>
            <path d="M7 6.5 L17 6.5 M6.5 7 L17.5 17.5 M6.5 17 L17.5 6.5 M7 17.5 L17 17.5"/>
          </svg>
        </div>
        <transition name="fade">
          <span v-if="!collapsed" class="logo-text">无人机监管平台</span>
        </transition>
      </div>

      <el-menu :default-active="$route.path" :collapse="collapsed" router>
        <template v-for="grp in menuGroups">
          <li v-if="!collapsed && grp.items.length" :key="grp.key" class="menu-group-title">{{ grp.title }}</li>
          <el-menu-item v-for="m in grp.items" :key="m.path" :index="m.path">
            <el-icon><component :is="m.icon" /></el-icon>
            <template #title>{{ m.label }}</template>
          </el-menu-item>
        </template>
      </el-menu>

      <div class="aside-footer">
        <span :class="['ws-pill', wsOk ? 'on' : 'off']">
          <i></i>{{ !collapsed ? (wsOk ? '链路正常' : '链路断开') : '' }}
        </span>
      </div>
    </el-aside>

    <!-- 主区 -->
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <button class="collapse-btn" @click="collapsed = !collapsed">
            <el-icon :size="18"><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
          </button>
          <span class="crumb">{{ $route.meta.title }}</span>
          <span class="crumb-sub">/ 低空监管控制台</span>
        </div>
        <div class="header-right">
          <!-- 实时告警:铃铛 + 未读角标,点击展开最近告警(数据来自全局告警中心 store) -->
          <el-popover placement="bottom-end" :width="392" trigger="click" popper-class="alert-pop"
                      @show="onBellOpen">
            <template #reference>
              <span class="alert-bell" :class="{ ringing: alertCenter.unread > 0 }" title="实时告警">
                <el-icon :size="18"><Bell /></el-icon>
                <span v-if="alertCenter.unread" class="alert-badge">
                  {{ alertCenter.unread > 99 ? '99+' : alertCenter.unread }}
                </span>
              </span>
            </template>
            <div class="ap-pop">
              <div class="ap-pop-head">
                <span>实时告警</span>
                <el-button link type="primary" size="small" @click="$router.push('/alerts')">告警中心 →</el-button>
              </div>
              <div class="ap-pop-list">
                <div v-if="!alertCenter.recent.length" class="ap-pop-empty">暂无告警</div>
                <div v-for="a in alertCenter.recent" :key="a.id"
                     class="ap-pop-item" :class="alertLevelClass(a.level)" @click="$router.push('/alerts')">
                  <div class="ap-pop-line">
                    <span class="ap-pop-type">{{ alertTypeText(a.type) }}</span>
                    <span class="ap-pop-time">{{ fmtAlertTime(a.createdAt) }}</span>
                  </div>
                  <div class="ap-pop-msg">{{ a.message }}</div>
                </div>
              </div>
            </div>
          </el-popover>
          <span class="clock">{{ clock }}</span>
          <el-dropdown @command="onCommand">
            <span class="user">
              <el-avatar :size="30" style="background: linear-gradient(135deg,#155eef,#0ea5e9)">{{ initial }}</el-avatar>
              <span class="nick">{{ nickname }}</span>
              <el-icon color="#98a2b3"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <component :is="Component" @ws-status="wsOk = $event" />
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Fold, Expand, ArrowDown, Bell } from '@element-plus/icons-vue'
import http from '../api'
import {
  alertCenter, pushAlert, loadRecentAlerts,
  alertTypeText, alertLevelClass, fmtAlertTime
} from '../stores/alertCenter'

const router = useRouter()
const collapsed = ref(false)
const wsOk = ref(false)
const nickname = ref(localStorage.getItem('nickname') || 'admin')
const initial = computed(() => (nickname.value || 'A').charAt(0))

// 动态菜单:后端 /menus/mine 按角色下发(图标已全局注册,字符串名渲染)
const menus = ref([])
const menuGroups = computed(() => [
  { key: 'BIZ', title: '业务菜单', items: menus.value.filter(m => m.group === 'BIZ') },
  { key: 'SYS', title: '系统管理', items: menus.value.filter(m => m.group === 'SYS') }
])

const clock = ref('')
let timer = null
onMounted(async () => {
  clock.value = new Date().toLocaleString('zh-CN', { hour12: false })
  timer = setInterval(() => {
    clock.value = new Date().toLocaleString('zh-CN', { hour12: false })
  }, 1000)
  loadRecentAlerts()          // 铃铛初值:最近告警
  connectAlertWs()            // 顶栏自有告警 WS:任意页面都能实时收告警
  try {
    // 刷新页面后重新拉取菜单(登录响应里已存过一份,失败则回退)
    menus.value = (await http.get('/menus/mine')).map(m => ({
      path: m.path, label: m.name, icon: m.icon, group: m.group
    }))
  } catch (e) {
    const cached = JSON.parse(localStorage.getItem('menus') || '[]')
    menus.value = cached.map(m => ({ path: m.path, label: m.name, icon: m.icon, group: m.group }))
  }
})
onUnmounted(() => {
  clearInterval(timer)
  closeAlertWs()
})

/** 打开铃铛面板:清未读角标并刷新最近列表(处理状态可能已被更新) */
function onBellOpen() {
  alertCenter.unread = 0
  loadRecentAlerts()
}

/* ---------- 顶栏告警 WS(与监控页连接独立,全程在线) ---------- */
let alertWs = null
let alertWsTimer = null

function connectAlertWs() {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  alertWs = new WebSocket(`${proto}://${location.host}/ws/telemetry`)
  alertWs.onopen = () => {
    wsOk.value = true
    if (alertWsTimer) { clearTimeout(alertWsTimer); alertWsTimer = null }
  }
  alertWs.onclose = () => {
    wsOk.value = false
    alertWsTimer = setTimeout(connectAlertWs, 5000)
  }
  alertWs.onerror = () => alertWs?.close()
  alertWs.onmessage = (ev) => {
    try {
      const msg = JSON.parse(ev.data)
      if (msg.type === 'alert') pushAlert(msg.payload)
    } catch (e) { /* ignore */ }
  }
}

function closeAlertWs() {
  if (alertWsTimer) clearTimeout(alertWsTimer)
  if (alertWs) { alertWs.onclose = null; alertWs.close() }
}

async function onCommand(cmd) {
  if (cmd === 'logout') {
    try {
      await http.post('/auth/logout')
    } catch (e) { /* token 失效也继续退出 */ }
    localStorage.removeItem('token')
    localStorage.removeItem('nickname')
    localStorage.removeItem('roleCode')
    localStorage.removeItem('menus')
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.layout { height: 100%; }

.aside {
  display: flex; flex-direction: column;
  background: #fff;
  border-right: 1px solid var(--border);
  transition: width 0.25s;
  overflow: hidden;
}

.logo {
  display: flex; align-items: center; gap: 10px;
  padding: 16px 16px; cursor: pointer; white-space: nowrap;
  border-bottom: 1px solid var(--border);
}
.logo-icon {
  width: 34px; height: 34px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, #155eef, #0ea5e9);
  border-radius: 10px;
  box-shadow: 0 4px 10px -2px rgba(21, 94, 239, 0.4);
}
.logo-text {
  font-size: 15px; font-weight: 700; letter-spacing: 1px; color: #101828;
}

.el-menu { border-right: none; flex: 1; padding: 10px 10px; overflow-y: auto; }
.menu-group-title {
  list-style: none;
  padding: 14px 12px 6px;
  font-size: 11px; font-weight: 700; letter-spacing: 2px;
  color: #98a2b3;
}
.menu-group-title:not(:first-child) { border-top: 1px dashed var(--border); margin-top: 8px; }
:deep(.el-menu-item) {
  border-radius: 9px; margin: 3px 0; height: 42px; line-height: 42px;
  color: #475467; font-weight: 500;
}
:deep(.el-menu-item .el-icon) { color: #667085; }
:deep(.el-menu-item:hover) { background: #f2f6fd; color: #155eef; }
:deep(.el-menu-item:hover .el-icon) { color: #155eef; }
:deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, #eaf1ff, #f0f9ff);
  color: #155eef; font-weight: 600;
  box-shadow: inset 0 0 0 1px #d6e4ff;
}
:deep(.el-menu-item.is-active .el-icon) { color: #155eef; }

.aside-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--border);
  white-space: nowrap;
}
.ws-pill {
  display: inline-flex; align-items: center; gap: 7px;
  font-size: 12px; padding: 4px 10px; border-radius: 999px;
}
.ws-pill.on { color: #12b76a; background: #ecfdf3; }
.ws-pill.off { color: #f04438; background: #fef3f2; }
.ws-pill i { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
.ws-pill.on i { box-shadow: 0 0 6px #12b76a; animation: pulse-dot 2s infinite; }

@keyframes pulse-dot { 0%,100% { opacity: 1; } 50% { opacity: .4; } }

.header {
  height: 56px;
  display: flex; align-items: center; justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--border);
}

.header-left { display: flex; align-items: center; gap: 12px; }
.collapse-btn {
  width: 32px; height: 32px; border-radius: 8px;
  border: 1px solid var(--border); background: #fff;
  display: flex; align-items: center; justify-content: center;
  color: #475467; cursor: pointer; transition: all .2s;
}
.collapse-btn:hover { border-color: #155eef; color: #155eef; background: #f5f8ff; }
.crumb { font-size: 15px; font-weight: 700; color: #101828; }
.crumb-sub { font-size: 13px; color: #98a2b3; }

.header-right { display: flex; align-items: center; gap: 18px; }
.clock { color: #667085; font-size: 13px; font-variant-numeric: tabular-nums; }
.user { display: flex; align-items: center; gap: 8px; cursor: pointer; }

/* 实时告警铃铛 */
.alert-bell {
  position: relative;
  display: inline-flex; align-items: center; justify-content: center;
  width: 32px; height: 32px; border-radius: 9px;
  border: 1px solid var(--border); background: #fff;
  color: #475467; cursor: pointer; transition: all .2s;
}
.alert-bell:hover { color: #155eef; border-color: #b8ccf7; background: #f5f8ff; }
.alert-bell.ringing { color: #f04438; border-color: #fda29b; background: #fef3f2; animation: bell-shake 1s infinite; }
.alert-badge {
  position: absolute; top: -6px; right: -7px;
  min-width: 17px; height: 17px; padding: 0 4px;
  border-radius: 999px; background: #f04438; color: #fff;
  font-size: 10.5px; font-weight: 700; line-height: 17px; text-align: center;
  box-shadow: 0 2px 6px -1px rgba(240, 68, 56, .5);
}
@keyframes bell-shake {
  0%, 100% { transform: rotate(0); }
  20% { transform: rotate(-12deg); }
  40% { transform: rotate(10deg); }
  60% { transform: rotate(-6deg); }
  80% { transform: rotate(4deg); }
}

/* 铃铛下拉面板(popover 全局样式,不能加 scoped) */
:global(.alert-pop .ap-pop) { display: flex; flex-direction: column; max-height: 420px; }
:global(.ap-pop-head) {
  display: flex; justify-content: space-between; align-items: center;
  padding: 2px 2px 8px; border-bottom: 1px solid #eaecf0;
  font-size: 13.5px; font-weight: 700; color: #101828;
}
:global(.ap-pop-list) { overflow-y: auto; padding: 8px 2px 2px; }
:global(.ap-pop-empty) { padding: 26px 0; text-align: center; color: #98a2b3; font-size: 12.5px; }
:global(.ap-pop-item) {
  padding: 8px 10px; margin-bottom: 7px; border-radius: 9px;
  border: 1px solid; cursor: pointer; transition: transform .15s;
}
:global(.ap-pop-item:hover) { transform: translateX(2px); }
:global(.ap-pop-item.lv-critical) { border-color: #fee4e2; background: #fff5f5; }
:global(.ap-pop-item.lv-warning) { border-color: #fef0c7; background: #fffcf5; }
:global(.ap-pop-item.lv-info) { border-color: #eaecf0; background: #fff; }
:global(.ap-pop-line) { display: flex; justify-content: space-between; margin-bottom: 2px; }
:global(.ap-pop-type) { font-size: 12px; font-weight: 700; color: #344054; }
:global(.ap-pop-item.lv-critical .ap-pop-type) { color: #d92d20; }
:global(.ap-pop-item.lv-warning .ap-pop-type) { color: #dc6803; }
:global(.ap-pop-time) { font-size: 11px; color: #98a2b3; }
:global(.ap-pop-msg) {
  font-size: 12px; color: #475467; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.nick { color: #344054; font-weight: 500; font-size: 13px; }

.main { padding: 0; overflow: hidden; background: var(--bg); }

.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
