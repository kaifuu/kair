/**
 * 地图提供商注册表 + SDK 按需加载器
 * 支持百度(GL)/ 高德 / 天地图,当前选择持久化于 localStorage,
 * 密钥优先读「地图管理」页保存的本地配置,其次读 .env。
 */
import { BMAP_AK } from './map'

export const PROVIDERS = [
  {
    id: 'baidu',
    name: '百度地图',
    short: '百',
    desc: 'WebGL 3D 楼块渲染,支持 73° 倾斜视角与个性化样式,监管态势主用图源',
    tags: ['3D 视角', '个性化样式', 'BD-09 坐标'],
    grad: 'linear-gradient(135deg,#155eef,#0ea5e9)'
  },
  {
    id: 'amap',
    name: '高德地图',
    short: '高',
    desc: 'JS API 2.0 矢量渲染,城市要素细腻,支持 3D 倾斜视角',
    tags: ['3D 视角', 'GCJ-02 坐标'],
    grad: 'linear-gradient(135deg,#0a7cff,#00c2ff)'
  },
  {
    id: 'tdt',
    name: '天地图',
    short: '天',
    desc: '国家地理信息公共服务官方底图,CGCS2000 坐标,政务合规之选',
    tags: ['官方底图', 'WGS-84 坐标'],
    grad: 'linear-gradient(135deg,#0e7490,#22d3ee)'
  }
]

const LS_PROVIDER = 'wrj.map.provider'
const LS_KEYS = 'wrj.map.keys'

/** 当前选中的提供商 id(默认百度) */
export function getProviderId() {
  const id = localStorage.getItem(LS_PROVIDER)
  return PROVIDERS.some((p) => p.id === id) ? id : 'baidu'
}

export function setProviderId(id) {
  localStorage.setItem(LS_PROVIDER, id)
}

export function getProviderMeta(id) {
  return PROVIDERS.find((p) => p.id === id)
}

/* ---------- 密钥管理(本地保存,无需重启) ---------- */

export function getMapKeys() {
  try {
    return JSON.parse(localStorage.getItem(LS_KEYS) || '{}')
  } catch (e) {
    return {}
  }
}

export function saveMapKeys(patch) {
  const next = { ...getMapKeys(), ...patch }
  localStorage.setItem(LS_KEYS, JSON.stringify(next))
  return next
}

/** 提供商密钥是否就绪(百度由 .env 提供,恒就绪) */
export function providerKeyReady(pid) {
  const keys = getMapKeys()
  if (pid === 'baidu') return true
  if (pid === 'amap') {
    return !!(keys.amap || import.meta.env.VITE_AMAP_KEY) &&
      !!(keys.amapSec || import.meta.env.VITE_AMAP_SEC)
  }
  if (pid === 'tdt') return !!(keys.tdt || import.meta.env.VITE_TDT_KEY)
  return false
}

/* ---------- SDK 按需加载 ---------- */

const loading = {}

function injectScript(src) {
  return new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = src
    s.async = true
    s.onload = resolve
    s.onerror = () => reject(new Error('地图脚本加载失败,请检查网络与密钥'))
    document.head.appendChild(s)
  })
}

/** SDK 为异步级联加载,注入后轮询等待命名空间就绪 */
function pollReady(check, timeout = 15000) {
  return new Promise((resolve, reject) => {
    let waited = 0
    const timer = setInterval(() => {
      if (check()) {
        clearInterval(timer)
        resolve()
      } else if ((waited += 150) > timeout) {
        clearInterval(timer)
        reject(new Error('地图脚本就绪超时'))
      }
    }, 150)
  })
}

export function loadMapSdk(pid = getProviderId()) {
  if (loading[pid]) return loading[pid]
  const task = (async () => {
    if (pid === 'baidu') {
      if (!BMAP_AK) throw new Error('百度地图 AK 未配置,请在 frontend/.env 设置 VITE_BMAP_AK(参考 .env.example)')
      if (!window.BMapGL?.Map) {
        // 百度 GL 的 api 脚本是加载器:同步引入时靠 document.write 级联真实 JS 包,
        // 动态注入必须带 callback 参数走 JSONP 通知,否则 BMapGL 永远不就绪
        await new Promise((resolve, reject) => {
          const cb = '__wrj_bmap_ready'
          window[cb] = resolve
          const s = document.createElement('script')
          s.src = `https://api.map.baidu.com/api?v=1.0&type=webgl&ak=${BMAP_AK}&callback=${cb}`
          s.onerror = () => reject(new Error('百度地图脚本加载失败,请检查网络与 AK'))
          document.head.appendChild(s)
        })
        await pollReady(() => window.BMapGL?.Map)
      }
      return window.BMapGL
    }
    if (pid === 'amap') {
      if (!window.AMap?.Map) {
        const key = getMapKeys().amap || import.meta.env.VITE_AMAP_KEY || ''
        const sec = getMapKeys().amapSec || import.meta.env.VITE_AMAP_SEC || ''
        if (!key || !sec) throw new Error('高德地图未配置 Key/安全密钥,请到「地图管理」填写')
        window._AMapSecurityConfig = { securityJsCode: sec }
        await injectScript(`https://webapi.amap.com/maps?v=2.0&key=${key}`)
        await pollReady(() => window.AMap?.Map)
      }
      return window.AMap
    }
    if (pid === 'tdt') {
      if (!window.T?.Map) {
        const tk = getMapKeys().tdt || import.meta.env.VITE_TDT_KEY || ''
        if (!tk) throw new Error('天地图未配置密钥,请到「地图管理」填写')
        await injectScript(`https://api.tianditu.gov.cn/api?v=4.0&tk=${tk}`)
        await pollReady(() => window.T?.Map)
      }
      return window.T
    }
    throw new Error('未知地图提供商:' + pid)
  })()
  loading[pid] = task
  task.catch(() => { delete loading[pid] })
  return task
}
