import { createApp } from 'vue'
import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { ensureServerKeys } from './utils/mapProviders'
import './styles/index.css'

// 规避 element-plus 2.14 ElMessage 已知 TDZ bug:message 的 onClose 闭包引用了
// 声明在后的 instance,若消息在对话框关闭过渡的同一 tick 内创建并被同步关闭,
// 会抛 "Cannot access 'instance' before initialization"。延迟一个宏任务创建即可避开。
for (const k of ['success', 'warning', 'error', 'info']) {
  const raw = ElMessage[k]
  if (typeof raw === 'function') {
    ElMessage[k] = (...args) => setTimeout(() => raw(...args), 0)
  }
}

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(ElementPlus, { locale: zhCn })
app.use(router)
app.mount('#app')

// 后台预取服务端地图密钥(未登录/失败静默,不阻塞渲染)
ensureServerKeys().catch(() => {})
