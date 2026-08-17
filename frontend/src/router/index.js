import { createRouter, createWebHashHistory } from 'vue-router'
import Layout from '../views/Layout.vue'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { title: '登录' } },
  {
    path: '/',
    component: Layout,
    redirect: '/monitor',
    children: [
      { path: 'monitor', name: 'monitor', component: () => import('../views/Monitor.vue'), meta: { title: '实时监控' } },
      { path: 'drones', name: 'drones', component: () => import('../views/DroneList.vue'), meta: { title: '无人机管理' } },
      { path: 'pilots', name: 'pilots', component: () => import('../views/PilotList.vue'), meta: { title: '飞手管理' } },
      { path: 'tasks', name: 'tasks', component: () => import('../views/TaskList.vue'), meta: { title: '飞行任务' } },
      { path: 'fences', name: 'fences', component: () => import('../views/FenceList.vue'), meta: { title: '电子围栏' } },
      { path: 'alerts', name: 'alerts', component: () => import('../views/AlertList.vue'), meta: { title: '告警中心' } },
      { path: 'stats', name: 'stats', component: () => import('../views/Stats.vue'), meta: { title: '统计分析' } },
      { path: 'mapadmin', name: 'mapadmin', component: () => import('../views/MapAdmin.vue'), meta: { title: '地图管理' } }
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
