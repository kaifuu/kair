/**
 * 全局告警中心(模块级响应式单例,无 pinia 依赖):
 * - 顶栏铃铛(Layout)与监控页(Monitor)共用,WS 推入新告警,任意页面可读
 * - recent 保留最近 30 条;unread 在打开铃铛面板时清零
 */
import { reactive } from 'vue'
import http from '../api'

export const alertCenter = reactive({
  recent: [],   // 新 → 旧
  unread: 0
})

/** WS 新告警推入(按 id 去重,监控页与顶栏两条 WS 连接都会收到) */
export function pushAlert(a) {
  if (!a || a.id == null) return
  const list = alertCenter.recent
  if (list.some((x) => x.id === a.id)) return
  list.unshift(a)
  if (list.length > 30) list.length = 30
  alertCenter.unread++
}

/** 初次进入拉取最近告警填充铃铛(不计未读) */
export async function loadRecentAlerts() {
  try {
    const res = await http.get('/alerts', { params: { page: 1, size: 12 } })
    alertCenter.recent = res.items || []
  } catch (e) { /* 未登录/失败静默 */ }
}

/** 告警类型中文文案(与告警中心页/演练页共用口径) */
export function alertTypeText(type) {
  return {
    GEOFENCE_BREACH: '禁飞区闯入', ALTITUDE_EXCEED: '超限高',
    LOW_BATTERY: '低电量', SIGNAL_LOST: '失联',
    NO_LICENSE: '黑飞嫌疑', TASK_OVERDUE: '超时未归',
    PREDICTED_BREACH: '预测闯入', CONFLICT_ALERT: '多机冲突',
    BATTERY_ANOMALY: '电量骤降', ALTITUDE_JUMP: '高度突变',
    SIGNAL_WEAK: '信号弱'
  }[type] || '告警'
}

export function alertLevelClass(level) {
  return { CRITICAL: 'lv-critical', WARNING: 'lv-warning', INFO: 'lv-info' }[level] || 'lv-info'
}

export function fmtAlertTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(5, 16)
}
