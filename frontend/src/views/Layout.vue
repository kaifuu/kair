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
import { Fold, Expand, ArrowDown } from '@element-plus/icons-vue'
import http from '../api'

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
onUnmounted(() => clearInterval(timer))

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
.nick { color: #344054; font-weight: 500; font-size: 13px; }

.main { padding: 0; overflow: hidden; background: var(--bg); }

.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
