/** 百度地图 AK(浏览器端 JavaScript API GL,配置于 frontend/.env 的 VITE_BMAP_AK) */
export const BMAP_AK = import.meta.env.VITE_BMAP_AK || ''

/** 亮色科技风个性化地图样式(百度 GL 专用) */
export const DARK_MAP_STYLE = {
  styleJson: [
    { featureType: 'background', elementType: 'all', stylers: { color: '#eef4fb' } },
    { featureType: 'land', elementType: 'all', stylers: { color: '#f2f6fc' } },
    { featureType: 'water', elementType: 'all', stylers: { color: '#cfe5f7' } },
    { featureType: 'green', elementType: 'all', stylers: { color: '#e3f0e6' } },
    { featureType: 'road', elementType: 'geometry', stylers: { color: '#ffffff', visibility: 'simplified' } },
    { featureType: 'road', elementType: 'labels', stylers: { color: '#8ba3c4', visibility: 'simplified' } },
    { featureType: 'highway', elementType: 'geometry', stylers: { color: '#e9f0f9' } },
    { featureType: 'building', elementType: 'all', stylers: { color: '#e6edf7' } },
    { featureType: 'building', elementType: 'labels', stylers: { visibility: 'off' } },
    { featureType: 'poilabel', elementType: 'all', stylers: { color: '#7d94b8', visibility: 'simplified' } },
    { featureType: 'districtlabel', elementType: 'labels', stylers: { color: '#5b7398' } },
    { featureType: 'districtlabel', elementType: 'labels icon', stylers: { visibility: 'off' } },
    { featureType: 'railway', elementType: 'all', stylers: { visibility: 'off' } },
    { featureType: 'subway', elementType: 'all', stylers: { visibility: 'off' } },
    { featureType: 'manmade', elementType: 'all', stylers: { color: '#eaeff8' } }
  ]
}

/* ---------- 图标:统一生成 SVG data-URL,百度/高德/天地图三引擎共用 ---------- */

/**
 * 解析围栏几何部件(围栏渲染统一出口):
 * - 单形状围栏:归一为 [{shape, radius, points}](shape/radius 取自围栏本体)
 * - 复合围栏(MULTI):pointsJson 本身即部件数组 [{shape, radius, points},...]
 * 解析失败/无点返回 []
 */
export function parseFenceShapes(fence) {
  let raw = []
  try { raw = JSON.parse(fence.pointsJson || '[]') } catch (e) { return [] }
  if (!Array.isArray(raw) || !raw.length) return []
  if (raw[0] && typeof raw[0] === 'object' && 'points' in raw[0]) {
    return raw.filter((p) => p && Array.isArray(p.points) && p.points.length)
  }
  return [{ shape: fence.shape, radius: fence.radius, points: raw }]
}


function svgUrl(svg) {
  return 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(svg)))
}

/** 无人机飞行图标(科技蓝,由 heading 控制旋转) */
export function droneSvg(heading = 0, color = '#155eef') {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="52" height="52" viewBox="0 0 52 52">
    <g transform="translate(26,26)">
      <g transform="rotate(${heading})">
        <circle cx="-13" cy="-13" r="6" fill="none" stroke="${color}" stroke-width="2.4"/>
        <circle cx="13" cy="-13" r="6" fill="none" stroke="${color}" stroke-width="2.4"/>
        <circle cx="-13" cy="13" r="6" fill="none" stroke="${color}" stroke-width="2.4"/>
        <circle cx="13" cy="13" r="6" fill="none" stroke="${color}" stroke-width="2.4"/>
        <path d="M-10 -10 L0 -6 L10 -10 M-10 10 L0 6 L10 10 M-9 -9 L9 9 M-9 9 L9 -9" stroke="${color}" stroke-width="2" opacity="0.9"/>
        <path d="M0 -16 L4 -9 L0 -11 L-4 -9 Z" fill="${color}"/>
      </g>
      <circle r="3.4" fill="#fff" stroke="${color}" stroke-width="2"/>
      <circle r="7" fill="none" stroke="${color}" stroke-width="1.2" opacity="0.45">
        <animate attributeName="r" values="5;13;5" dur="2.2s" repeatCount="indefinite"/>
        <animate attributeName="opacity" values="0.5;0;0.5" dur="2.2s" repeatCount="indefinite"/>
      </circle>
    </g>
  </svg>`
  return svgUrl(svg)
}

/** 归航点/机巢图标(按无人机状态着色) */
export function homeSvg(color = '#667085') {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 22 22">
    <circle cx="11" cy="11" r="9" fill="${color}22" stroke="${color}" stroke-width="1.6"/>
    <circle cx="11" cy="11" r="3.4" fill="${color}"/>
  </svg>`
  return svgUrl(svg)
}

/** 航点图标(航线展示用) */
export function routePointSvg(color = '#155eef') {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 18 18">
    <circle cx="9" cy="9" r="7" fill="${color}22" stroke="${color}" stroke-width="1.5"/>
    <circle cx="9" cy="9" r="2.6" fill="${color}"/>
  </svg>`
  return svgUrl(svg)
}

/* ---------- 物联网设备图标:分类徽标 + 分类色,三引擎共用 ---------- */

/** 设备分类元信息(中文名 / 主题色 / 面板字段单位提示) */
export const DEVICE_META = {
  DRONE: { label: '无人机', color: '#155eef', glyph: 'drone' },
  DOCK: { label: '智能机库', color: '#0e9384', glyph: 'dock' },
  CAMERA: { label: '视频监控', color: '#dd2590', glyph: 'camera' },
  WEATHER: { label: '气象站', color: '#e04f16', glyph: 'weather' },
  ADSB: { label: 'ADS-B', color: '#3639a4', glyph: 'adsb' },
  GATEWAY: { label: 'PLC/网关', color: '#6c2bd9', glyph: 'gateway' },
  SENSOR: { label: '传感设备', color: '#12b76a', glyph: 'sensor' },
  // ---- 无人机反制设备 ----
  RADAR: { label: '警戒雷达', color: '#dc6803', glyph: 'radar' },
  RADIO_DETECT: { label: '无线电探测', color: '#0ba5ec', glyph: 'radiodetect' },
  EO_TRACK: { label: '光电跟踪', color: '#0e9f6e', glyph: 'eo' },
  RADIO_JAM: { label: '无线电压制', color: '#d92d20', glyph: 'jam' },
  LASER: { label: '激光处置', color: '#e0004d', glyph: 'laser' },
  NET_CAPTURE: { label: '网捕无人机', color: '#7a5af8', glyph: 'net' }
}

/**
 * 反制设备元数据(设备管理表单默认值 / 攻防演练布防共用):
 * role: detect=探测类 counter=反制类;defaultRange:类型默认扫描/作用范围 m;
 * action/actText:反制类动作(JAM 驱离 / DESTROY 击落 / CAPTURE 捕获)
 */
export const COUNTER_META = {
  RADAR: { label: '警戒雷达', role: 'detect', defaultRange: 5000, shape: 'circle' },
  RADIO_DETECT: { label: '无线电探测', role: 'detect', defaultRange: 3500, shape: 'circle' },
  EO_TRACK: { label: '光电跟踪', role: 'detect', defaultRange: 2500, shape: 'sector', eo: true },
  RADIO_JAM: { label: '无线电压制', role: 'counter', defaultRange: 1800, action: 'JAM', actText: '压制驱离' },
  LASER: { label: '激光处置', role: 'counter', defaultRange: 1000, action: 'DESTROY', actText: '激光击落' },
  NET_CAPTURE: { label: '网捕无人机', role: 'counter', defaultRange: 1200, action: 'CAPTURE', actText: '网捕捕获' }
}

export const deviceMeta = (category) => DEVICE_META[category] || DEVICE_META.SENSOR

/** 各分类图形(44×44 viewBox 内,统一过 22,22 中心) */
const DEVICE_GLYPHS = {
  drone: (c) => `<g stroke="${c}" stroke-width="2" fill="none">
      <circle cx="-8" cy="-8" r="4.4"/><circle cx="8" cy="-8" r="4.4"/>
      <circle cx="-8" cy="8" r="4.4"/><circle cx="8" cy="8" r="4.4"/>
      <path d="M-8 -8 L8 8 M-8 8 L8 -8" opacity="0.85"/>
    </g><circle cx="0" cy="0" r="2.6" fill="${c}"/>`,
  dock: (c) => `<path d="M-10 6 L-10 -2 Q-10 -8 0 -8 Q10 -8 10 -2 L10 6 Z" fill="none" stroke="${c}" stroke-width="2"/>
    <path d="M-6 6 L-6 0 M6 6 L6 0" stroke="${c}" stroke-width="2"/>`,
  camera: (c) => `<rect x="-9" y="-6" width="13" height="12" rx="2.5" fill="none" stroke="${c}" stroke-width="2"/>
    <path d="M5 -3 L10 -6.5 L10 6.5 L5 3 Z" fill="${c}" opacity="0.9"/>
    <circle cx="-2.5" cy="0" r="2" fill="${c}"/>`,
  weather: (c) => `<circle cx="-4" cy="-4" r="3.6" fill="none" stroke="${c}" stroke-width="2"/>
    <path d="M-9 7 A5 5 0 0 1 -8 -1 A6.5 6.5 0 0 1 5 -2.5 A4.6 4.6 0 1 1 6 7 Z" fill="none" stroke="${c}" stroke-width="2"/>`,
  adsb: (c) => `<path d="M0 -10 L2 -3 L10 1 L10 3.5 L2 2 L1.5 7 L4.5 9 L4.5 10.5 L0 9.5 L-4.5 10.5 L-4.5 9 L-1.5 7 L-2 2 L-10 3.5 L-10 1 L-2 -3 Z" fill="${c}"/>`,
  gateway: (c) => `<rect x="-7" y="-7" width="14" height="14" rx="2" fill="none" stroke="${c}" stroke-width="2"/>
    <path d="M-7 -3 H-11 M-7 3 H-11 M7 -3 H11 M7 3 H11" stroke="${c}" stroke-width="2"/>
    <circle cx="0" cy="0" r="2.4" fill="${c}"/>`,
  sensor: (c) => `<circle cx="-4" cy="4" r="2.2" fill="${c}"/>
    <path d="M-1 1 A7 7 0 0 1 8 4" fill="none" stroke="${c}" stroke-width="2" opacity="0.9"/>
    <path d="M2 -2 A11.5 11.5 0 0 1 12 4" fill="none" stroke="${c}" stroke-width="2" opacity="0.55"/>
    <path d="M-12 4 A7 7 0 0 1 -6 -3" fill="none" stroke="${c}" stroke-width="2" opacity="0.9"/>
    <path d="M-16 4 A11.5 11.5 0 0 1 -8 -6.5" fill="none" stroke="${c}" stroke-width="2" opacity="0.55"/>`,
  // ---- 反制设备图形 ----
  radar: (c) => `<path d="M-9 -8 A11 11 0 0 1 9 6" fill="none" stroke="${c}" stroke-width="2.4"/>
    <path d="M-9 -8 A5.5 5.5 0 0 1 -2 -1" fill="none" stroke="${c}" stroke-width="2" opacity="0.7"/>
    <path d="M0 -2 L6 9 M0 -2 L-2 9" stroke="${c}" stroke-width="2"/>
    <circle cx="0" cy="-3" r="2.6" fill="${c}"/>`,
  radiodetect: (c) => `<path d="M0 -10 L2.5 2 L-2.5 2 Z" fill="${c}"/>
    <path d="M0 2 L0 9 M-5 9 H5" stroke="${c}" stroke-width="2"/>
    <path d="M-4 -6 A8 8 0 0 0 -4 4" fill="none" stroke="${c}" stroke-width="1.8" opacity="0.8"/>
    <path d="M4 -6 A8 8 0 0 1 4 4" fill="none" stroke="${c}" stroke-width="1.8" opacity="0.8"/>`,
  eo: (c) => `<circle cx="0" cy="0" r="8.5" fill="none" stroke="${c}" stroke-width="2"/>
    <path d="M0 -12 V-4 M0 4 V12 M-12 0 H-4 M4 0 H12" stroke="${c}" stroke-width="2"/>
    <circle cx="0" cy="0" r="2" fill="${c}"/>`,
  jam: (c) => `<path d="M-3 -9 L-1 3 L3 3 L1 -9 Z" fill="none" stroke="${c}" stroke-width="2"/>
    <path d="M0 3 V10" stroke="${c}" stroke-width="2"/>
    <path d="M5 -7 A9 9 0 0 1 8 1" fill="none" stroke="${c}" stroke-width="1.8" opacity="0.85"/>
    <path d="M-5 -7 A9 9 0 0 0 -8 1" fill="none" stroke="${c}" stroke-width="1.8" opacity="0.85"/>
    <path d="M-9 -10 L9 10" stroke="${c}" stroke-width="2.4" opacity="0.9"/>`,
  laser: (c) => `<rect x="-8" y="-6" width="7" height="12" rx="2" fill="none" stroke="${c}" stroke-width="2"/>
    <path d="M-1 0 L9 -7 M-1 0 L9 0 M-1 0 L9 7" stroke="${c}" stroke-width="1.8" opacity="0.85"/>
    <circle cx="9" cy="0" r="1.8" fill="${c}"/>`,
  net: (c) => `<g stroke="${c}" stroke-width="1.6" fill="none">
      <path d="M-9 -6 H9 M-9 0 H9 M-9 6 H9 M-9 -9 V9 M0 -9 V9 M9 -9 V9" opacity="0.9"/>
    </g>
    <path d="M-11 -9 Q0 -14 11 -9" fill="none" stroke="${c}" stroke-width="2"/>`
}

/**
 * 物联网设备分类图标(在线脉冲圈)
 * @param {string} category Device.Category
 * @param {Object} opts { online:true 在线加脉冲, color 覆盖分类色 }
 */
export function deviceSvg(category = 'SENSOR', { online = true, color } = {}) {
  const meta = deviceMeta(category)
  const c = color || meta.color
  const glyph = (DEVICE_GLYPHS[meta.glyph] || DEVICE_GLYPHS.sensor)(c)
  const pulse = online
    ? `<circle r="16" fill="none" stroke="${c}" stroke-width="1.2" opacity="0.5">
        <animate attributeName="r" values="14;21;14" dur="2.4s" repeatCount="indefinite"/>
        <animate attributeName="opacity" values="0.5;0;0.5" dur="2.4s" repeatCount="indefinite"/>
      </circle>`
    : ''
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="44" height="44" viewBox="0 0 44 44">
    <g transform="translate(22,22)">
      ${pulse}
      <rect x="-13" y="-13" width="26" height="26" rx="8" fill="#ffffff" stroke="${c}" stroke-width="2.2"/>
      <rect x="-13" y="-13" width="26" height="26" rx="8" fill="${c}1f"/>
      ${glyph}
    </g>
  </svg>`
  return svgUrl(svg)
}

/* ---------- 设备图标解析(设备管理表单 / 监控地图 / 轨迹回放共用) ----------
 * 设备 icon 字段三种取值:
 *   ''                → 按分类默认徽标(无人机实时/回放图标随航向旋转)
 *   'preset:<分类键>' → 预设徽标(同 deviceSvg 渲染,可跨分类选用,如传感器用摄像头图形)
 *   dataURL / http(s) → 用户上传的自定义图标(静态,不随航向旋转)
 */

/** 图标预设清单(设备表单选择器渲染用) */
export const ICON_PRESETS = Object.entries(DEVICE_META)
  .map(([key, meta]) => ({ key, label: meta.label }))

/** 解析设备图标为可直接使用的地址(<img src> / marker svg) */
export function resolveDeviceIcon(d, { online = true } = {}) {
  const icon = d && d.icon
  if (icon) {
    if (icon.startsWith('preset:')) return deviceSvg(icon.slice(7), { online })
    return icon
  }
  return deviceSvg(d?.category || 'SENSOR', { online })
}

/** 仅返回"用户上传"的自定义图标地址;预设与默认都返回空(由地图按默认/航向渲染) */
export function customDeviceIcon(d) {
  const icon = d && d.icon
  return icon && !icon.startsWith('preset:') ? icon : ''
}
