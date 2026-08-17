/**
 * 坐标系互转工具
 * 平台数据全链路以百度 BD-09 存储;高德使用 GCJ-02,天地图使用 WGS-84(CGCS2000),
 * 地图适配层负责在渲染/交互边界做互转,业务代码始终只面对 BD-09。
 */
const X_PI = Math.PI * 3000 / 180
const PI = Math.PI
const SEMI = 6378245.0            // 克拉索夫斯基椭球长半轴
const EE = 0.00669342162296594323 // 偏心率平方

function outOfChina(lng, lat) {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}

function deltaLat(x, y) {
  let ret = -100 + 2 * x + 3 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  ret += (20 * Math.sin(6 * x * PI) + 20 * Math.sin(2 * x * PI)) * 2 / 3
  ret += (20 * Math.sin(y * PI) + 40 * Math.sin(y / 3 * PI)) * 2 / 3
  ret += (160 * Math.sin(y / 12 * PI) + 320 * Math.sin(y * PI / 30)) * 2 / 3
  return ret
}

function deltaLng(x, y) {
  let ret = 300 + x + 2 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  ret += (20 * Math.sin(6 * x * PI) + 20 * Math.sin(2 * x * PI)) * 2 / 3
  ret += (20 * Math.sin(x * PI) + 40 * Math.sin(x / 3 * PI)) * 2 / 3
  ret += (150 * Math.sin(x / 12 * PI) + 300 * Math.sin(x / 30 * PI)) * 2 / 3
  return ret
}

/** 火星坐标(GCJ-02) → WGS-84 */
export function gcj02ToWgs84(lng, lat) {
  if (outOfChina(lng, lat)) return { lng, lat }
  const dLat = deltaLat(lng - 105, lat - 35)
  const dLng = deltaLng(lng - 105, lat - 35)
  const radLat = (lat / 180) * PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  const dl = (dLat * 180) / ((SEMI * (1 - EE)) / (magic * sqrtMagic) * PI)
  const dg = (dLng * 180) / (SEMI / sqrtMagic * Math.cos(radLat) * PI)
  return { lng: lng * 2 - (lng + dg), lat: lat * 2 - (lat + dl) }
}

/** WGS-84 → 火星坐标(GCJ-02) */
export function wgs84ToGcj02(lng, lat) {
  if (outOfChina(lng, lat)) return { lng, lat }
  const dLat = deltaLat(lng - 105, lat - 35)
  const dLng = deltaLng(lng - 105, lat - 35)
  const radLat = (lat / 180) * PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  const dl = (dLat * 180) / ((SEMI * (1 - EE)) / (magic * sqrtMagic) * PI)
  const dg = (dLng * 180) / (SEMI / sqrtMagic * Math.cos(radLat) * PI)
  return { lng: lng + dg, lat: lat + dl }
}

/** 百度 BD-09 → 火星坐标(GCJ-02) */
export function bd09ToGcj02(lng, lat) {
  const x = lng - 0.0065
  const y = lat - 0.006
  const z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI)
  const theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI)
  return { lng: z * Math.cos(theta), lat: z * Math.sin(theta) }
}

/** 火星坐标(GCJ-02) → 百度 BD-09 */
export function gcj02ToBd09(lng, lat) {
  const z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * X_PI)
  const theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * X_PI)
  return { lng: z * Math.cos(theta) + 0.0065, lat: z * Math.sin(theta) + 0.006 }
}

/** 百度 BD-09 → WGS-84(天地图) */
export function bd09ToWgs84(lng, lat) {
  const g = bd09ToGcj02(lng, lat)
  return gcj02ToWgs84(g.lng, g.lat)
}

/** WGS-84(天地图) → 百度 BD-09 */
export function wgs84ToBd09(lng, lat) {
  const g = wgs84ToGcj02(lng, lat)
  return gcj02ToBd09(g.lng, g.lat)
}
