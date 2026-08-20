import { createRouter, createWebHashHistory } from 'vue-router'
import Layout from '../views/Layout.vue'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { title: '登录' } },
  {
    path: '/',
    component: Layout,
    redirect: '/monitor',
    children: [
      // ---- 业务菜单 ----
      { path: 'monitor', name: 'monitor', component: () => import('../views/Monitor.vue'), meta: { title: '实时监控' } },
      { path: 'devices', name: 'devices', component: () => import('../views/DeviceList.vue'), meta: { title: '设备管理' } },
      { path: 'pilots', name: 'pilots', component: () => import('../views/PilotList.vue'), meta: { title: '飞手管理' } },
      { path: 'tasks', name: 'tasks', component: () => import('../views/TaskList.vue'), meta: { title: '飞行任务' } },
      { path: 'fences', name: 'fences', component: () => import('../views/FenceList.vue'), meta: { title: '电子围栏' } },
      { path: 'alerts', name: 'alerts', component: () => import('../views/AlertList.vue'), meta: { title: '告警中心' } },
      { path: 'stats', name: 'stats', component: () => import('../views/Stats.vue'), meta: { title: '统计分析' } },
      { path: 'mapadmin', name: 'mapadmin', component: () => import('../views/MapAdmin.vue'), meta: { title: '地图管理' } },
      // ---- 系统管理 ----
      { path: 'protocols', name: 'protocols', component: () => import('../views/ProtocolList.vue'), meta: { title: '协议管理' } },
      { path: 'messages', name: 'messages', component: () => import('../views/DeviceMessageList.vue'), meta: { title: '报文管理' } },
      { path: 'sys/users', name: 'sys-users', component: () => import('../views/sys/SysUserList.vue'), meta: { title: '人员管理' } },
      { path: 'sys/roles', name: 'sys-roles', component: () => import('../views/sys/SysRoleList.vue'), meta: { title: '角色管理' } },
      { path: 'sys/menus', name: 'sys-menus', component: () => import('../views/sys/SysMenuList.vue'), meta: { title: '菜单管理' } },
      { path: 'sys/orgs', name: 'sys-orgs', component: () => import('../views/sys/SysOrgList.vue'), meta: { title: '组织管理' } },
      { path: 'sys/tenants', name: 'sys-tenants', component: () => import('../views/sys/SysTenantList.vue'), meta: { title: '租户管理' } },
      { path: 'sys/logs', name: 'sys-logs', component: () => import('../views/sys/SysLogList.vue'), meta: { title: '日志管理' } },
      { path: 'msgadmin', name: 'msgadmin', component: () => import('../views/MsgAdmin.vue'), meta: { title: '消息管理' } },
      { path: 'models', name: 'models', component: () => import('../views/ModelAdmin.vue'), meta: { title: '模型配置' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/monitor' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = (to.meta.title ? to.meta.title + ' · ' : '') + '无人机监管平台'
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
