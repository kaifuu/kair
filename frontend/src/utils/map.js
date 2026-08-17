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
