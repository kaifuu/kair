/**
 * 地图提供商注册表 + SDK 按需加载器
 * 支持百度(GL)/ 高德 / 天地图 + 自定义 XYZ 瓦片提供商。
 *
 * 数据来源(权威在前):
 *  1. 服务器「地图管理」/api/map-providers(厂商/凭证/自定义瓦片/默认设置,登录后生效)
 *  2. 本机 localStorage(未登录回落:静态内置 + 旧版本地自定义)
 * 密钥取值链:服务器厂商凭证 → 本机 localStorage → .env(VITE_*)。
 *
 * 同步约束:getRegistry()/getProviderId() 必须保持同步(登录页/监控页同步调用),
 * 服务端列表由 ensureServerProviders() 异步预热进模块级缓存后再由调用方刷新。
 */
import http from '../api'
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
    grad: 'linear-gradient(135deg,#00b96b,#7be6b0)'
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

/** 渲染引擎中文名(自定义 XYZ 提供商按 engine 借用引擎渲染) */
export const ENGINE_NAME = { baidu: '百度', amap: '高德', tdt: '天地图' }

/** 厂商字典(新增/编辑弹窗渲染凭证字段;engine 为 CUSTOM 的渲染引擎缺省) */
export const VENDORS = [
  {
    code: 'BAIDU', label: '百度', engine: 'baidu',
    creds: [{ key: 'ak', label: 'AK', ph: '百度地图开放平台 AK' }]
  },
  {
    code: 'AMAP', label: '高德', engine: 'amap',
    creds: [
      { key: 'key', label: 'Key', ph: '高德开放平台 Key' },
      { key: 'secret', label: '安全密钥', ph: '高德安全密钥(jscode)' }
    ]
  },
  {
    code: 'TDT', label: '天地图', engine: 'tdt',
    creds: [{ key: 'tk', label: 'TK', ph: '天地图开发引擎密钥' }]
  },
  {
    code: 'CUSTOM', label: '自定义', engine: '',
    custom: true,
    creds: [{ key: 'key', label: '瓦片密钥(可空)', ph: 'tileUrl 中 {key} 占位替换' }]
  }
]

export const vendorInfo = (code) => VENDORS.find((v) => v.code === code) || VENDORS[3]

const DEFAULT_GRAD = {
  BAIDU: 'linear-gradient(135deg,#337cff,#00c8ff)',
  AMAP: 'linear-gradient(135deg,#00b96b,#7be6b0)',
  TDT: 'linear-gradient(135deg,#1a7f6e,#8fd26b)',
  CUSTOM: 'linear-gradient(135deg,#6366f1,#a855f7)'
}

const LS_PROVIDER = 'wrj.map.provider'
const LS_KEYS = 'wrj.map.keys'
const LS_CUSTOM = 'wrj.map.customProviders'

/* ---------- localStorage 安全读写(损坏时清除回落默认) ---------- */

function readJson(key, fallback) {
  try {
    const v = JSON.parse(localStorage.getItem(key))
    return v ?? fallback
  } catch (e) {
    localStorage.removeItem(key)
    return fallback
  }
}

function writeJson(key, val) {
  localStorage.setItem(key, JSON.stringify(val))
}

/* ---------- 服务器厂商配置(登录后权威来源) ---------- */

let serverProviders = null     // null=未加载;数组=启用的厂商配置(注册表卡片形态)
let serverLoading = null

/** SysMapProvider(服务端) → 注册表卡片(凭证拍平,字段与静态 PROVIDERS 对齐) */
function mapServerProvider(p) {
  let creds = {}
  try {
    creds = JSON.parse(p.credentialsJson || '{}')
  } catch (e) { /* 凭证损坏按未配置处理 */ }
  const builtIn = ['baidu', 'amap', 'tdt'].includes(p.code)
  const engine = p.vendor === 'CUSTOM' ? (p.engine || 'tdt') : (p.vendor || '').toLowerCase()
  return {
    id: p.code,
    serverId: p.id,
    builtIn,
    vendor: p.vendor || 'CUSTOM',
    name: p.name || p.code,
    short: (p.name || p.code).trim().charAt(0) || '图',
    desc: p.description || '',
    tags: builtIn ? [] : ['XYZ 瓦片', `引擎:${ENGINE_NAME[engine] || engine}`],
    grad: p.grad || DEFAULT_GRAD[p.vendor] || DEFAULT_GRAD.CUSTOM,
    enabled: p.enabled !== false,
    isDefault: p.isDefault === true,
    engine,
    type: p.vendor === 'CUSTOM' ? 'xyz' : undefined,
    tileUrl: p.tileUrl || '',
    key: creds.key || '',
    credentials: creds
  }
}

/**
 * 预热服务端厂商配置(仅启用项进入注册表缓存)。
 * 未登录时静默跳过(不缓存,登录后重取);失败静默回落本地静态配置。
 */
export async function ensureServerProviders(force = false) {
  if (serverProviders && !force) return serverProviders
  if (!localStorage.getItem('token')) return null
  if (!serverLoading || force) {
    serverLoading = http.get('/map-providers')
      .then((list) => {
        serverProviders = (Array.isArray(list) ? list : [])
          .filter((p) => p && p.enabled !== false)
          .map(mapServerProvider)
        return serverProviders
      })
      .finally(() => { serverLoading = null })
  }
  try {
    return await serverLoading
  } catch (e) {
    return null
  }
}

/** 旧版密钥预热兼容入口(现为厂商配置预热别名) */
export const ensureServerKeys = ensureServerProviders

/** 服务端厂商配置 CRUD(地图管理页):成功后刷新缓存 */
export async function fetchProviderRows() {
  return (await http.get('/map-providers')) || []
}

export async function saveProviderRow(payload) {
  const row = payload.id
    ? await http.put(`/map-providers/${payload.id}`, payload.body)
    : await http.post('/map-providers', payload.body)
  await ensureServerProviders(true)
  return row
}

export async function deleteProviderRow(id) {
  await http.delete(`/map-providers/${id}`)
  await ensureServerProviders(true)
}

export async function setDefaultProviderRow(id) {
  await http.put(`/map-providers/${id}/default`)
  await ensureServerProviders(true)
}

/* ---------- 提供商注册表(内置 + 自定义 XYZ) ---------- */

function getCustomProviders() {
  const list = readJson(LS_CUSTOM, [])
  return Array.isArray(list) ? list.filter((c) => c && c.id && c.type === 'xyz') : []
}

/** 全量注册表:服务端配置(已加载)优先;未登录回落静态内置 + 旧版本地自定义 */
export function getRegistry() {
  const customs = getCustomProviders().map((c) => ({
    ...c,
    tags: [...(c.tags || []), 'XYZ 瓦片', `引擎:${ENGINE_NAME[c.engine] || c.engine}`]
  }))
  if (serverProviders) {
    const codes = new Set(serverProviders.map((p) => p.id))
    return [...serverProviders, ...customs.filter((c) => !codes.has(c.id))]
  }
  return [...PROVIDERS.map((p) => ({ ...p, builtIn: true })), ...customs]
}

/** 当前选中的提供商 id(本机选择 > 服务端默认底图 > baidu;停用项跳过) */
export function getProviderId() {
  const reg = getRegistry()
  const id = localStorage.getItem(LS_PROVIDER)
  if (id && reg.some((p) => p.id === id && p.enabled !== false)) return id
  const def = reg.find((p) => p.isDefault && p.enabled !== false)
  if (def) return def.id
  return reg.some((p) => p.id === 'baidu') ? 'baidu' : (reg[0]?.id || 'baidu')
}

export function setProviderId(id) {
  localStorage.setItem(LS_PROVIDER, id)
}

export function getProviderMeta(id) {
  return getRegistry().find((p) => p.id === id)
}

/** 提供商实际使用的渲染引擎:内置即自身,自定义按 engine 字段 */
export function resolveEngine(meta) {
  return meta?.builtIn ? meta.id : (meta?.engine || 'tdt')
}

/* ---------- 旧版本地自定义 CRUD(未登录回落用,登录后以服务端为准) ---------- */

export function saveCustomProvider(cfg) {
  const list = getCustomProviders()
  const now = Date.now()
  const next = {
    type: 'xyz',
    engine: 'tdt',
    key: '',
    desc: '',
    ...cfg,
    short: (cfg.short || cfg.name || '').trim().charAt(0) || '图',
    builtIn: false
  }
  const idx = list.findIndex((c) => c.id === next.id)
  if (idx >= 0) {
    next.createdAt = list[idx].createdAt || now
    list[idx] = next
  } else {
    next.id = next.id || `custom_${now.toString(36)}`
    next.createdAt = now
    list.push(next)
  }
  writeJson(LS_CUSTOM, list)
  return next
}

export function removeCustomProvider(id) {
  const list = getCustomProviders()
  if (!list.some((c) => c.id === id)) return false
  writeJson(LS_CUSTOM, list.filter((c) => c.id !== id))
  return true
}

/* ---------- 密钥管理(服务器厂商凭证 → 本机 localStorage → .env) ---------- */

export function getMapKeys() {
  return readJson(LS_KEYS, {})
}

export function saveMapKeys(patch) {
  const next = { ...getMapKeys(), ...patch }
  writeJson(LS_KEYS, next)
  return next
}

/** 三级回落后的生效引擎密钥(百度/高德/天地图) */
export function effectiveMapKeys() {
  const local = getMapKeys()
  const server = serverProviders || []
  const byVendor = (v) => server.find((p) => p.vendor === v)?.credentials || {}
  return {
    baidu: byVendor('BAIDU').ak || local.baiduAk || BMAP_AK || '',
    amap: byVendor('AMAP').key || local.amap || import.meta.env.VITE_AMAP_KEY || '',
    amapSec: byVendor('AMAP').secret || local.amapSec || import.meta.env.VITE_AMAP_SEC || '',
    tdt: byVendor('TDT').tk || local.tdt || import.meta.env.VITE_TDT_KEY || ''
  }
}

/** 引擎 SDK 密钥是否就绪 */
function engineKeyReady(engine) {
  const keys = effectiveMapKeys()
  if (engine === 'baidu') return !!keys.baidu
  if (engine === 'amap') return !!(keys.amap && keys.amapSec)
  if (engine === 'tdt') return !!keys.tdt
  return false
}

/** 提供商是否就绪可启用:内置看引擎密钥;自定义需引擎密钥 + 瓦片地址 */
export function providerKeyReady(pid) {
  const meta = getRegistry().find((p) => p.id === pid)
  if (!meta) return false
  return engineKeyReady(resolveEngine(meta)) && (meta.builtIn || !!meta.tileUrl)
}

/* ---------- SDK 按需加载(按引擎缓存,自定义提供商复用引擎 SDK) ---------- */

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

function loadEngineSdk(engine) {
  if (loading[engine]) return loading[engine]
  const task = (async () => {
    await ensureServerProviders()
    if (engine === 'baidu') {
      const ak = effectiveMapKeys().baidu
      if (!ak) throw new Error('百度地图 AK 未配置,请在「地图管理」维护厂商凭证,或在 frontend/.env 设置 VITE_BMAP_AK')
      if (!window.BMapGL?.Map) {
        // 百度 GL 的 api 脚本是加载器:同步引入时靠 document.write 级联真实 JS 包,
        // 动态注入必须带 callback 参数走 JSONP 通知,否则 BMapGL 永远不就绪
        await new Promise((resolve, reject) => {
          const cb = '__wrj_bmap_ready'
          window[cb] = resolve
          const s = document.createElement('script')
          s.src = `https://api.map.baidu.com/api?v=1.0&type=webgl&ak=${ak}&callback=${cb}`
          s.onerror = () => reject(new Error('百度地图脚本加载失败,请检查网络与 AK'))
          document.head.appendChild(s)
        })
        await pollReady(() => window.BMapGL?.Map)
      }
      return window.BMapGL
    }
    if (engine === 'amap') {
      if (!window.AMap?.Map) {
        const keys = effectiveMapKeys()
        if (!keys.amap || !keys.amapSec) throw new Error('高德地图未配置 Key/安全密钥,请到「地图管理」维护厂商凭证')
        window._AMapSecurityConfig = { securityJsCode: keys.amapSec }
        await injectScript(`https://webapi.amap.com/maps?v=2.0&key=${keys.amap}`)
        await pollReady(() => window.AMap?.Map)
      }
      return window.AMap
    }
    if (engine === 'tdt') {
      if (!window.T?.Map) {
        const tk = effectiveMapKeys().tdt
        if (!tk) throw new Error('天地图未配置密钥,请到「地图管理」维护厂商凭证')
        await injectScript(`https://api.tianditu.gov.cn/api?v=4.0&tk=${tk}`)
        await pollReady(() => window.T?.Map)
      }
      return window.T
    }
    throw new Error('未知地图引擎:' + engine)
  })()
  loading[engine] = task
  task.catch(() => { delete loading[engine] })
  return task
}

/** 加载提供商所需 SDK:解析注册表元信息后按引擎注入(自定义 XYZ 复用引擎 SDK) */
export function loadMapSdk(pid = getProviderId()) {
  const meta = getProviderMeta(pid)
  if (!meta) throw new Error('未知地图提供商:' + pid)
  return loadEngineSdk(resolveEngine(meta))
}
