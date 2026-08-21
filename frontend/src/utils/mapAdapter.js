/**
 * 统一地图适配层
 * 对外暴露一致的地图 API(addMarker/addCircle/addPolygon/addLabel/flyTo...),
 * 内部按当前提供商分发给 百度 GL / 高德 JS API 2.0 / 天地图 4.0。
 *
 * 坐标约定:业务侧一律传/收 BD-09({lng,lat}),
 * 适配层负责与 GCJ-02(高德)/ WGS-84(天地图)互转,数据模型不感知差异。
 */
import { loadMapSdk, getProviderId, getProviderMeta, resolveEngine } from './mapProviders'
import { DARK_MAP_STYLE, droneSvg, homeSvg, routePointSvg } from './map'
import { bd09ToGcj02, bd09ToWgs84, gcj02ToBd09, wgs84ToBd09 } from './coord'

export { droneSvg, homeSvg, routePointSvg }

/** BD-09(数据) → 各引擎原生的 lng/lat 对象 */
const toNative = {
  baidu: (p) => ({ lng: p.lng, lat: p.lat }),
  amap: (p) => bd09ToGcj02(p.lng, p.lat),
  tdt: (p) => bd09ToWgs84(p.lng, p.lat)
}

/** 各引擎原生坐标 → BD-09(数据) */
const toData = {
  baidu: (lng, lat) => ({ lng, lat }),
  amap: (lng, lat) => gcj02ToBd09(lng, lat),
  tdt: (lng, lat) => wgs84ToBd09(lng, lat)
}

/** 同级视野下高德/天地图比百度低 1 级 */
const ZOOM_ADJ = { baidu: 0, amap: -1, tdt: -1 }

/** 统一的线/面样式入参 → 各引擎样式 */
function polyStyle(o = {}) {
  return {
    strokeColor: o.color || '#155eef',
    strokeWeight: o.weight ?? 1.5,
    strokeOpacity: o.opacity ?? 0.85,
    fillColor: o.fill || o.color || '#155eef',
    fillOpacity: o.fillOpacity ?? 0.1,
    ...(o.dashed ? { strokeStyle: 'dashed' } : {})
  }
}

/** XYZ 瓦片模板填充({z}/{x}/{y} 标准占位符) */
function fillTile(tpl, x, y, z) {
  return tpl.replace(/\{z\}/g, z).replace(/\{x\}/g, x).replace(/\{y\}/g, y)
}

/** css 对象 → styleText(供高德/天地图的 HTML 标签用) */
function cssText(css = {}) {
  return Object.entries(css)
    .map(([k, v]) => `${k.replace(/[A-Z]/g, (m) => '-' + m.toLowerCase())}:${v}`)
    .join(';')
}

/**
 * 创建统一地图实例
 * @param {HTMLElement} el 容器
 * @param {Object} opts { center:{lng,lat}, zoom, view3d, customStyle }
 */
export async function createMap(el, opts = {}) {
  const meta = getProviderMeta(getProviderId())
  if (!meta) throw new Error('未知地图提供商,请到「地图管理」检查配置')
  const engine = resolveEngine(meta)
  const sdk = await loadMapSdk(meta.id)
  // 自定义 XYZ 提供商:{key} 占位符替换为提供商瓦片密钥后透传给各引擎叠加
  const xyz = meta.type === 'xyz' && meta.tileUrl
    ? { url: meta.tileUrl.replace(/\{key\}/g, meta.key || '') }
    : null
  if (engine === 'amap') return amapFacade(sdk, el, { ...opts, xyz })
  if (engine === 'tdt') return tdtFacade(sdk, el, { ...opts, xyz })
  return baiduFacade(sdk, el, { ...opts, xyz })
}

/* ============================== 可视化绘制(跨引擎统一) ============================== */

/**
 * 绘制交互的 DOM 事件源:直接监听地图容器,而非引擎 map 事件。
 * 原因:①引擎对矢量覆盖物做命中测试,点击落在覆盖物上(如预览圆/已有围栏)时
 * map click 不再触发,绘制会"点了没反应";②百度 GL 在容器内自建 BMap_mask
 * 事件层并拦截传播。因此全部走捕获阶段监听,任何引擎都无法拦截。
 * 拖拽平移后浏览器仍会补发 click,按 mousedown→click 位移阈值过滤。
 * @param el 地图容器 @param toGeo (x,y)像素 → {lng,lat}(BD-09 业务坐标)
 */
function domDrawSource(el, toGeo) {
  let downX = 0, downY = 0
  const on = (type, h) => el.addEventListener(type, h, true)
  on('mousedown', (e) => { downX = e.clientX; downY = e.clientY })
  const still = (e) => Math.hypot(e.clientX - downX, e.clientY - downY) < 5
  const geo = (e) => {
    const r = el.getBoundingClientRect()
    return toGeo(e.clientX - r.left, e.clientY - r.top)
  }
  return {
    onClick: (cb) => on('click', (e) => { if (still(e)) cb(geo(e)) }),
    onMousemove: (cb) => on('mousemove', (e) => cb(geo(e))),
    onDblclick: (cb) => on('dblclick', (e) => { if (still(e)) cb(geo(e)) })
  }
}

/** 两点球面距离(米) */
export function distMeters(a, b) {
  const rad = Math.PI / 180
  const dLat = (b.lat - a.lat) * rad
  const dLng = (b.lng - a.lng) * rad
  const s = Math.sin(dLat / 2) ** 2 +
    Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(dLng / 2) ** 2
  return 2 * 6371000 * Math.asin(Math.sqrt(s))
}

/** 球面多边形面积(m²):等距圆柱投影(经度按顶点均值纬度校正)+ 鞋带公式,工具箱面积测算用 */
export function sphericalArea(pts) {
  if (!pts || pts.length < 3) return 0
  const rad = Math.PI / 180
  const latRef = (pts.reduce((s, p) => s + p.lat, 0) / pts.length) * rad
  const kx = 111320 * Math.cos(latRef)
  const ky = 111320
  let sum = 0
  for (let i = 0; i < pts.length; i++) {
    const a = pts[i]
    const b = pts[(i + 1) % pts.length]
    sum += (a.lng * kx) * (b.lat * ky) - (b.lng * kx) * (a.lat * ky)
  }
  return Math.abs(sum) / 2
}

/** 圆 → 闭合折线轮廓(正 n 边形):圆形预览用它渲染,绕开 BMapGL Circle 的内部 _bounds 崩溃 */
export function circleOutline(c, r, n = 72) {
  const rad = Math.PI / 180
  const latM = 111320
  const lngM = 111320 * Math.max(0.1, Math.cos(c.lat * rad))
  const out = []
  for (let i = 0; i < n; i++) {
    const brg = 2 * Math.PI * i / n
    out.push({ lng: c.lng + r * Math.sin(brg) / lngM, lat: c.lat + r * Math.cos(brg) / latM })
  }
  return out
}

/**
 * 当前视野包围盒(BD-09):容器四角 + 顶边中点像素反算经纬度,统一三引擎口径,
 * 规避各引擎 bounds API 的坐标系差异。高倾角 3D 下个别采样点可能越过地平线外插异常,
 * 过滤非有限值;跨度 > 90° 视为异常回退引擎原生 bounds(传入的 fallback 函数)。
 */
function viewBounds(el, toGeo, nativeFallback) {
  try {
    const w = el.clientWidth
    const h = el.clientHeight
    if (!w || !h) return nativeFallback?.() || null
    const pts = [[0, 0], [w, 0], [0, h], [w, h], [w / 2, 0]]
      .map(([x, y]) => {
        try { return toGeo(x, y) } catch (e) { return null }
      })
      .filter((p) => p && isFinite(p.lng) && isFinite(p.lat))
    if (pts.length < 3) return nativeFallback?.() || null
    const lngs = pts.map((p) => p.lng)
    const lats = pts.map((p) => p.lat)
    const sw = { lng: Math.min(...lngs), lat: Math.min(...lats) }
    const ne = { lng: Math.max(...lngs), lat: Math.max(...lats) }
    if (ne.lng - sw.lng > 90 || ne.lat - sw.lat > 90) return nativeFallback?.() || null
    return { sw, ne }
  } catch (e) {
    return nativeFallback?.() || null
  }
}

/**
 * 为地图实例挂载统一绘制能力(围栏点/线/面可视化勾画):
 * - 多边形/线形:单击加顶点,双击或 finishDraw() 完成
 * - 圆形:单击定圆心,移动预览,再单击定半径
 * 覆盖物全部走统一 overlay API,百度/高德/天地图/自定义瓦片四引擎交互一致,
 * 不依赖各厂商互不兼容的 DrawingManager 扩展库。
 * api.startDraw({shape,keep,onUpdate,onFinish}) / finishDraw(forceKeep)->bool / undoDrawPoint / clearDraw / stopDraw
 * keep=true 时完成一个形状不销毁覆盖物:onFinish 第三参返回覆盖物数组(交调用方管理),
 * 状态重置后可继续绘制下一个形状,用于一次绘制多个围栏批量保存。
 */
function withDraw(api, raw) {
  let d = null   // {shape, points, times[], cursor, radius, verts[], line, circle, centerMk, onUpdate, onFinish}

  // 注意:addCircle/addPolygon 返回各引擎原生覆盖物(无 destroy),
  // 统一走 api.remove(),它会兼容包装对象与原生覆盖物
  const destroyShape = () => {
    if (!d) return
    d.verts.forEach((v) => api.remove(v))
    d.verts = []
    api.remove(d.line); d.line = null
    api.remove(d.circle); d.circle = null
    api.remove(d.centerMk); d.centerMk = null
  }

  // onUpdate 第三参 cursor(光标处 BD-09 坐标):工具箱测距/面积的实时标签跟随光标用;
  // FenceList 等旧调用方只用前两参,保持向后兼容
  const notify = () => d?.onUpdate?.(d.points, d.radius || 0, d.cursor)

  const updatePreview = () => {
    if (!d) return
    if (d.shape === 'CIRCLE') {
      if (d.points[0] && d.cursor) d.radius = distMeters(d.points[0], d.cursor)
      if (d.points[0]) {
        if (!d.centerMk) d.centerMk = api.addMarker(d.points[0], { svg: routePointSvg('#155eef'), size: 16 })
        if (d.radius > 1) {
          // 圆形预览用闭合折线(正 72 边形)而非 Circle 覆盖物:
          // BMapGL 的 Circle 在 setRadius/移除时会触发内部 _bounds 每帧崩溃,
          // 折线 setPath 三引擎均稳定;真实圆几何由后端按中心+半径生成
          const ring = circleOutline(d.points[0], d.radius)
          if (!d.circle) d.circle = api.addPolyline(ring, { color: '#155eef', weight: 2, opacity: 0.9 })
          else d.circle.setPath(ring)
        } else {
          api.remove(d.circle)
          d.circle = null
        }
      }
      return
    }
    const path = [...d.points]
    if (d.cursor) path.push(d.cursor)
    if (d.shape === 'POLYGON' && path.length >= 3) path.push(path[0])
    if (path.length >= 2) {
      if (d.line) d.line.setPath(path)
      else d.line = api.addPolyline(path, { color: '#155eef', weight: 2.5, opacity: 0.9, dashed: d.shape !== 'POLYGON' })
    }
  }

  const addVertex = (p) => {
    d.points.push(p)
    d.times.push(Date.now())
    d.verts.push(api.addMarker(p, { svg: routePointSvg('#155eef'), size: 15 }))
    updatePreview()
    notify()
  }

  const finish = (keep) => {
    if (!d) return false
    if (d.shape === 'CIRCLE') {
      if (d.points.length < 1 || !d.radius || d.radius < 10) return false
    } else {
      const min = d.shape === 'LINE' ? 2 : 3
      // 双击完成时浏览器先触发两次 click,末尾会多一个重复点;
      // 双击间隔内(500ms)加的点视为第二击,剔除(坐标阈值在缩放小时会漏判)
      const n = d.points.length
      if (n > min && Date.now() - d.times[n - 1] < 500) {
        d.points.pop()
        d.times.pop()
        api.remove(d.verts.pop())
      }
      if (d.points.length < min) return false
    }
    const out = { ...d }
    const bundle = [...d.verts, d.line, d.circle, d.centerMk].filter(Boolean)
    if (keep) {
      // keep 模式:保留已完成形状的覆盖物,重置状态继续绘制下一个
      d.verts = []; d.line = null; d.circle = null; d.centerMk = null
      d.points = []; d.times = []; d.cursor = null; d.radius = 0
      notify()
      out.onFinish?.(out.points, out.radius || 0, bundle)
      return true
    }
    api.stopDraw()
    out.onFinish?.(out.points, out.radius || 0)
    return true
  }

  api.startDraw = (opts = {}) => {
    api.stopDraw()
    d = {
      shape: opts.shape || 'POLYGON', points: [], times: [], cursor: null, radius: 0,
      verts: [], line: null, circle: null, centerMk: null, keep: !!opts.keep,
      onUpdate: opts.onUpdate, onFinish: opts.onFinish
    }
    raw.lockDblclickZoom(true)
  }

  api.finishDraw = (forceKeep) => finish(forceKeep ?? d?.keep)

  api.undoDrawPoint = () => {
    if (!d) return
    if (d.shape === 'CIRCLE') {
      d.points = []
      d.radius = 0
      destroyShape()
    } else if (d.points.length) {
      d.points.pop()
      d.times.pop()
      api.remove(d.verts.pop())
      updatePreview()
    }
    notify()
  }

  api.clearDraw = () => {
    if (!d) return
    d.points = []
    d.times = []
    d.radius = 0
    destroyShape()
    notify()
  }

  api.stopDraw = () => {
    destroyShape()
    d = null
    raw.lockDblclickZoom(false)
  }

  raw.onClick((p) => {
    if (!d) return
    if (d.shape === 'CIRCLE') {
      if (!d.points.length) {
        d.points = [p]
        updatePreview()
        notify()
      } else {
        d.radius = distMeters(d.points[0], p)
        finish(d.keep)
      }
    } else {
      addVertex(p)
    }
  })

  raw.onMousemove((p) => {
    if (!d) return
    d.cursor = p
    updatePreview()
    notify()
  })

  raw.onDblclick(() => {
    if (d && d.shape !== 'CIRCLE') finish(d.keep)
  })
}



function baiduFacade(BMapGL, el, opts) {
  const P = (p) => new BMapGL.Point(p.lng, p.lat)
  // BMapGL 事件里 e.point/pointMC 为墨卡托米制坐标,经纬度必须取 e.latlng
  const evLL = (e) => e.latlng || e.point
  const map = new BMapGL.Map(el, { enableMapClick: false })
  const init = { center: opts.center || { lng: 116.404, lat: 39.925 }, zoom: opts.zoom || 12 }
  map.centerAndZoom(P(init.center), init.zoom)
  map.enableScrollWheelZoom(true)
  if (opts.customStyle) map.setMapStyleV2(DARK_MAP_STYLE)
  if (opts.xyz) {
    // 百度瓦片原点/切片方案与标准 XYZ 不同,通用瓦片源会错位(表单已警示)
    map.addTileLayer(new BMapGL.TileLayer({
      getTileUrl: (coord, zoom) => fillTile(opts.xyz.url, coord.x, coord.y, zoom)
    }))
  }
  const controls = {}   // name -> 控件实例(setControl 管理)
  // 容器像素 → BD-09:pixelToPoint 已实测返回经纬度(e.point 墨卡托的坑见下 evLL)
  const pxToData = (x, y) => {
    const p = map.pixelToPoint(new BMapGL.Pixel(x, y))
    return { lng: p.lng, lat: p.lat }
  }

  const api = {
    provider: 'baidu',
    raw: map,
    supports3d: true,

    /** 统一控件开关(name ∈ scale|mapType|compass3d)。必须持有原实例再 removeControl,新建一个去 remove 无效 */
    setControl(name, on) {
      const defs = {
        scale: () => new BMapGL.ScaleControl({ anchor: window.BMAP_ANCHOR_BOTTOM_LEFT, offset: new BMapGL.Size(12, 12) }),
        mapType: () => new BMapGL.MapTypeControl({ anchor: window.BMAP_ANCHOR_TOP_RIGHT, offset: new BMapGL.Size(12, 96) }),
        compass3d: () => new BMapGL.NavigationControl3D({ anchor: window.BMAP_ANCHOR_BOTTOM_RIGHT, offset: new BMapGL.Size(16, 16) })
      }
      if (!defs[name]) return false
      try {
        if (on) {
          if (!controls[name]) { controls[name] = defs[name](); map.addControl(controls[name]) }
        } else if (controls[name]) {
          map.removeControl(controls[name])
          delete controls[name]
        }
        return true
      } catch (e) {
        return false
      }
    },

    panBy(dx, dy) {
      // BMapGL panBy 为「内容平移」语义(正 x 内容右移 → 视野西移),
      // 统一为「视野平移」语义(正 dx 视野向东看),故取反
      map.panBy?.(-dx, -dy)
    },

    getZoom() { return map.getZoom() },

    getCenter() {
      const c = map.getCenter()
      return { lng: c.lng, lat: c.lat }
    },

    getBounds() {
      // 优先引擎原生 bounds(BD-09):3D 高倾角下像素反算会向地平线外插(实测经度可跨 20°+)
      try {
        const b = map.getBounds()
        const sw = b.getSouthWest()
        const ne = b.getNorthEast()
        if (sw && ne) return { sw: { lng: sw.lng, lat: sw.lat }, ne: { lng: ne.lng, lat: ne.lat } }
      } catch (e) { /* 回落像素法 */ }
      return viewBounds(el, pxToData)
    },

    onViewChange(cb) {
      map.addEventListener('moveend', cb)
      map.addEventListener('zoomend', cb)
    },

    /** 复位到 createMap 时的初始视野 */
    resetView() {
      map.flyTo(P(init.center), init.zoom)
    },

    applyView3d(on) {
      map.setTilt(on ? 73 : 0)
      map.setHeading(on ? 64.5 : 0)
    },

    addMarker(pt, m = {}) {
      const size = m.size || 52
      // BMapGL.Icon 是三参构造 (url, size:Size, opts:{anchor,...})(SDK 源码 function lk(me,T,C));
      // 此前把 {size,anchor} 对象当第 2 参传入,T.width=undefined → 尺寸/锚点 NaN → 图标全部不渲染
      const mkIcon = (url) => new BMapGL.Icon(url,
        new BMapGL.Size(size, size),
        { anchor: new BMapGL.Size(size / 2, size / 2) })
      const marker = new BMapGL.Marker(P(pt), { icon: mkIcon(m.svg || droneSvg(m.heading || 0)) })
      if (m.onClick) marker.addEventListener('click', m.onClick)
      map.addOverlay(marker)
      return {
        update(next) {
          // m.rotate:自定义颜色但需随航向旋转(攻防演练敌机);静态 svg 图标不旋转
          if (next.heading != null && (!m.svg || m.rotate)) {
            marker.setIcon(mkIcon(droneSvg(next.heading, m.color)))
          }
          if (next.lng != null) marker.setPosition(P(next))
        },
        destroy() { map.removeOverlay(marker) }
      }
    },

    addLabel(pt, html, { dx = 0, dy = 0, css = {}, onClick } = {}) {
      const label = new BMapGL.Label(html, {
        position: P(pt),
        offset: new BMapGL.Size(dx, dy)
      })
      label.setStyle(css)
      if (onClick) label.addEventListener('click', onClick)
      map.addOverlay(label)
      return {
        setPosition: (p) => label.setPosition(P(p)),
        setContent: (h) => label.setContent(h),
        destroy: () => map.removeOverlay(label)
      }
    },

    addCircle(pt, r, o = {}) {
      // BMapGL 原生 Circle 在被移除/周期性重绘时会触发内部 _bounds.setMinMax 每帧崩溃,
      // 与绘制预览同源 —— 静态围栏圆也统一用 72 边形面渲染
      const c = new BMapGL.Polygon(circleOutline(pt, r).map(P), polyStyle(o))
      map.addOverlay(c)
      return c
    },

    addPolygon(pts, o = {}) {
      const c = new BMapGL.Polygon(pts.map(P), polyStyle(o))
      map.addOverlay(c)
      return c
    },

    addPolyline(pts, o = {}) {
      const l = new BMapGL.Polyline(pts.map(P), polyStyle(o))
      map.addOverlay(l)
      return {
        setPath: (ps) => l.setPath(ps.map(P)),
        destroy: () => map.removeOverlay(l)
      }
    },

    remove(o) {
      if (!o) return
      // facade 包装对象(marker/polyline/label)自带自有 destroy(内部走 removeOverlay);
      // 原生覆盖物(polygon 等)的 destroy 是 SDK 原型方法:只清内部状态(会把 _bounds
      // 置成普通数组)却不从地图摘除,残留的"已销毁"覆盖物令渲染循环每帧抛 setMinMax 崩溃
      if (Object.prototype.hasOwnProperty.call(o, 'destroy')) o.destroy()
      else map.removeOverlay(o)
    },

    flyTo(pt, zoom) {
      map.flyTo(P(pt), zoom ?? 12)
    },

    setViewport(pts) {
      map.setViewport(pts.map(P))
    },

    onClick(cb) {
      map.addEventListener('click', (e) => cb({ lng: evLL(e).lng, lat: evLL(e).lat }))
    },

    /** 容器像素 → BD-09 业务坐标(拖放布防等 DOM 交互换算;绕过引擎覆盖物命中层) */
    toData: pxToData,

    /** 容器尺寸变化后强制重算画布(全屏/侧栏收起后调用) */
    resize() {
      try { map.resize?.() } catch (e) { /* 引擎无此方法时走兜底 */ }
      window.dispatchEvent(new Event('resize'))
    },

    destroy() {
      // 先清空覆盖物再销毁:BMapGL destroy 不解除覆盖物引用,
      // 残留覆盖物会随全局事件总线触发 _updateGraph → _bounds 已失效导致每帧崩溃
      try { map.clearOverlays() } catch (e) { /* 忽略 */ }
      map.destroy?.()
    }
  }

  const bmapDraw = domDrawSource(el, pxToData)
  bmapDraw.lockDblclickZoom = (lock) => {
    try { lock ? map.disableDoubleClickZoom?.() : map.enableDoubleClickZoom?.() } catch (e) { /* 无此方法时忽略 */ }
  }
  withDraw(api, bmapDraw)

  // 3D 罗盘不再随 view3d 自动添加:控件统一交由工具箱 setControl('compass3d') 管理
  if (opts.view3d) api.applyView3d(true)
  return api
}

/* ============================== 高德 JS API 2.0 ============================== */

function amapFacade(AMap, el, opts) {
  const LL = (p) => {
    const g = toNative.amap(p)
    return [g.lng, g.lat]
  }
  const map = new AMap.Map(el, {
    viewMode: '3D',
    zoom: (opts.zoom || 12) + ZOOM_ADJ.amap,
    center: LL(opts.center || { lng: 116.404, lat: 39.925 })
  })
  const init = { center: opts.center || { lng: 116.404, lat: 39.925 }, zoom: opts.zoom || 12 }
  if (opts.xyz) {
    // 高德瓦片占位符为 [x]/[y]/[z],由标准 XYZ 模板转写
    const tileUrl = opts.xyz.url
      .replace(/\{x\}/g, '[x]').replace(/\{y\}/g, '[y]').replace(/\{z\}/g, '[z]')
    map.add(new AMap.TileLayer({ tileUrl, zooms: [3, 20] }))
  }
  const controls = {}    // name -> 控件实例
  const desired = {}     // name -> 最近一次开关意图(插件异步加载竞态防护)

  const api = {
    provider: 'amap',
    raw: map,
    supports3d: true,

    /** 统一控件开关:Scale/MapType/ControlBar 均为异步插件,加载回调到达时核对最新意图 */
    setControl(name, on) {
      const PLUGIN = { scale: 'AMap.Scale', mapType: 'AMap.MapType', compass3d: 'AMap.ControlBar' }
      if (!PLUGIN[name]) return false
      desired[name] = on
      if (!on) {
        if (controls[name]) {
          try { map.removeControl(controls[name]) } catch (e) { /* ignore */ }
          delete controls[name]
        }
        return true
      }
      AMap.plugin(PLUGIN[name], () => {
        // 迟到的插件回调:意图已翻转或已存在实例则不再添加
        if (desired[name] !== true || controls[name]) return
        try {
          const inst = name === 'scale' ? new AMap.Scale()
            : name === 'mapType' ? new AMap.MapType({ position: { top: '96px', right: '12px' } })
              : new AMap.ControlBar({ position: { right: '20px', bottom: '20px' } })
          map.addControl(inst)
          controls[name] = inst
        } catch (e) { /* 插件缺失/构造失败时静默 */ }
      })
      return true
    },

    panBy(dx, dy) {
      // AMap panBy 同为「内容平移」语义(实测),统一为「视野平移」语义(正 dx 视野向东)取反
      map.panBy?.(-dx, -dy)
    },

    getZoom() { return map.getZoom() - ZOOM_ADJ.amap },

    getCenter() {
      const c = map.getCenter()
      return toData.amap(c.lng ?? c.getLng(), c.lat ?? c.getLat())
    },

    getBounds() {
      // 优先原生 bounds(GCJ-02 → BD-09),3D 视角下比像素反算稳
      try {
        const b = map.getBounds()
        const sw = b.getSouthWest()
        const ne = b.getNorthEast()
        if (sw && ne) {
          const a = toData.amap(sw.lng, sw.lat)
          const c = toData.amap(ne.lng, ne.lat)
          return { sw: a, ne: c }
        }
      } catch (e) { /* 回落像素法 */ }
      return viewBounds(el, (x, y) => {
        const g = map.containerToLngLat(new AMap.Pixel(x, y))
        return g ? toData.amap(g.lng, g.lat) : null
      })
    },

    onViewChange(cb) {
      map.on('moveend', cb)
      map.on('zoomend', cb)
    },

    /** 复位到 createMap 时的初始视野(复用 flyTo,内部已做 ZOOM_ADJ) */
    resetView() {
      api.flyTo(init.center, init.zoom)
    },

    applyView3d(on) {
      map.setPitch(on ? 55 : 0)
      map.setRotation(on ? -30 : 0)
    },

    addMarker(pt, m = {}) {
      const size = m.size || 52
      const mkIcon = (url) => new AMap.Icon({
        image: url,
        size: new AMap.Size(size, size),
        imageSize: new AMap.Size(size, size)
      })
      const marker = new AMap.Marker({ position: LL(pt), icon: mkIcon(m.svg || droneSvg(m.heading || 0)), anchor: 'center' })
      if (m.onClick) marker.on('click', m.onClick)
      map.add(marker)
      return {
        update(next) {
          if (next.heading != null && (!m.svg || m.rotate)) {
            marker.setIcon(mkIcon(droneSvg(next.heading, m.color)))
          }
          if (next.lng != null) marker.setPosition(LL(next))
        },
        destroy() { map.remove(marker) }
      }
    },

    addLabel(pt, html, { dx = 0, dy = 0, css = {}, onClick } = {}) {
      // 可点击标签:内容 div 保留 pointer-events(仅覆盖标签自身盒),否则点击穿透无法触发
      const style = `${cssText(css)}${onClick ? ';cursor:pointer' : ';pointer-events:none'}`
      const marker = new AMap.Marker({
        position: LL(pt),
        anchor: 'top-left',
        offset: new AMap.Pixel(dx, dy),
        content: `<div style="${style}">${html}</div>`,
        cursor: 'default'
      })
      if (onClick) marker.on('click', onClick)
      map.add(marker)
      return {
        setPosition: (p) => marker.setPosition(LL(p)),
        setContent: (h) => marker.setContent(`<div style="${style}">${h}</div>`),
        destroy: () => map.remove(marker)
      }
    },

    addCircle(pt, r, o = {}) {
      const c = new AMap.Circle({ center: LL(pt), radius: r, ...polyStyle(o) })
      map.add(c)
      return c
    },

    addPolygon(pts, o = {}) {
      const c = new AMap.Polygon({ path: pts.map(LL), ...polyStyle(o) })
      map.add(c)
      return c
    },

    addPolyline(pts, o = {}) {
      const l = new AMap.Polyline({ path: pts.map(LL), ...polyStyle(o) })
      map.add(l)
      return {
        setPath: (ps) => l.setPath(ps.map(LL)),
        destroy: () => map.remove(l)
      }
    },

    remove(o) {
      if (!o) return
      if (o.destroy) o.destroy()
      else map.remove(o)
    },

    flyTo(pt, zoom) {
      map.setZoomAndCenter((zoom ?? 12) + ZOOM_ADJ.amap, LL(pt))
    },

    setViewport(pts) {
      // 用隐形折线借用 setFitView 完成自适应视野
      const ghost = new AMap.Polyline({ path: pts.map(LL), strokeOpacity: 0, strokeWeight: 1 })
      map.add(ghost)
      map.setFitView([ghost], false, [60, 60, 60, 60])
      map.remove(ghost)
    },

    onClick(cb) {
      map.on('click', (e) => cb(toData.amap(e.lnglat.lng, e.lnglat.lat)))
    },

    /** 容器像素 → BD-09 业务坐标(拖放布防等 DOM 交互换算) */
    toData(x, y) {
      const g = map.containerToLngLat(new AMap.Pixel(x, y))
      return g ? toData.amap(g.lng, g.lat) : null
    },

    /** 容器尺寸变化后强制重算画布(高德 2.0 自适应,再补发 resize 兜底) */
    resize() {
      try { map.resize?.() } catch (e) { /* 引擎无此方法时走兜底 */ }
      window.dispatchEvent(new Event('resize'))
    },

    destroy() {
      map.destroy?.()
    }
  }

  const amapDraw = domDrawSource(el, (x, y) => {
    const g = map.containerToLngLat(new AMap.Pixel(x, y))
    return toData.amap(g.lng, g.lat)
  })
  amapDraw.lockDblclickZoom = (lock) => {
    try { map.setStatus?.({ doubleClickZoom: !lock }) } catch (e) { /* 无此方法时忽略 */ }
  }
  withDraw(api, amapDraw)

  if (opts.view3d) api.applyView3d(true)
  return api
}

/* ============================== 天地图 4.0 ============================== */

function tdtFacade(T, el, opts) {
  const LL = (p) => {
    const w = toNative.tdt(p)
    return new T.LngLat(w.lng, w.lat)
  }
  const lineStyle = (o = {}) => ({
    color: o.color || '#155eef',
    weight: o.weight ?? 1.5,
    opacity: o.opacity ?? 0.85,
    fillColor: o.fill || o.color || '#155eef',
    fillOpacity: o.fillOpacity ?? 0.1,
    ...(o.dashed ? { lineStyle: 'dashed' } : {})
  })

  const map = new T.Map(el)
  const init = { center: opts.center || { lng: 116.404, lat: 39.925 }, zoom: opts.zoom || 12 }
  map.centerAndZoom(LL(init.center), init.zoom + ZOOM_ADJ.tdt)
  map.enableScrollWheelZoom()
  if (opts.xyz) {
    // 天地图原生支持 {z}/{x}/{y} 模板,且同为 WGS-84 坐标系,叠加无偏移
    map.addLayer(new T.TileLayer(opts.xyz.url, { minZoom: 3, maxZoom: 18 }))
  }
  const controls = {}   // name -> 控件实例

  const api = {
    provider: 'tdt',
    raw: map,
    supports3d: false,   // 2D 底图引擎,工具箱据此置灰 3D 罗盘

    /** 统一控件开关:天地图 2D 无罗盘;控件构造/挂载差异较大,统一 try/catch 兜底返回 false */
    setControl(name, on) {
      if (name === 'compass3d') return false
      const defs = { scale: () => new T.Control.Scale(), mapType: () => new T.Control.MapType() }
      if (!defs[name]) return false
      try {
        if (on) {
          if (!controls[name]) { controls[name] = defs[name](); map.addControl(controls[name]) }
        } else if (controls[name]) {
          map.removeControl(controls[name])
          delete controls[name]
        }
        return true
      } catch (e) {
        return false
      }
    },

    panBy(dx, dy) {
      // 不用引擎 panBy(天地图方向语义未实测):取「当前中心平移 (dx,dy) 像素」处的地理点
      // 作为新中心再 panTo —— 视野语义方向确定,与百度/高德行为对齐
      try {
        const w = el.clientWidth
        const h = el.clientHeight
        const g = map.containerToLngLat(new T.Point(w / 2 + dx, h / 2 + dy))
        if (g) map.panTo(new T.LngLat(g.getLng(), g.getLat()))
      } catch (e) { /* ignore */ }
    },

    getZoom() { return map.getZoom() - ZOOM_ADJ.tdt },

    getCenter() {
      const c = map.getCenter()
      return toData.tdt(c.lng ?? c.getLng(), c.lat ?? c.getLat())
    },

    getBounds() {
      return viewBounds(el, (x, y) => {
        const g = map.containerToLngLat(new T.Point(x, y))
        return g ? toData.tdt(g.getLng(), g.getLat()) : null
      })
    },

    onViewChange(cb) {
      // moveend 文档未完全确认,额外挂 dragend 兜底(重复触发无害,重绘已 rAF 合并)
      map.addEventListener('moveend', cb)
      map.addEventListener('dragend', cb)
      map.addEventListener('zoomend', cb)
    },

    /** 复位到 createMap 时的初始视野(复用 flyTo,内部已做 ZOOM_ADJ) */
    resetView() {
      api.flyTo(init.center, init.zoom)
    },

    applyView3d() { /* 天地图为 2D 底图,无倾斜视角 */ },

    addMarker(pt, m = {}) {
      const size = m.size || 52
      const mkIcon = (url) => new T.Icon({
        iconUrl: url,
        iconSize: new T.Point(size, size),
        iconAnchor: new T.Point(size / 2, size / 2)
      })
      const marker = new T.Marker(LL(pt), { icon: mkIcon(m.svg || droneSvg(m.heading || 0)) })
      if (m.onClick) marker.addEventListener('click', m.onClick)
      map.addOverLay(marker)
      return {
        update(next) {
          if (next.heading != null && (!m.svg || m.rotate)) {
            marker.setIcon(mkIcon(droneSvg(next.heading, m.color)))
          }
          if (next.lng != null) marker.setLngLat(LL(next))
        },
        destroy() { map.removeOverLay(marker) }
      }
    },

    addLabel(pt, html, { dx = 0, dy = 0, css = {}, onClick } = {}) {
      const style = `${cssText(css)}${onClick ? ';cursor:pointer' : ';pointer-events:none'}`
      const label = new T.Label({
        text: `<div style="${style}">${html}</div>`,
        position: LL(pt),
        offset: new T.Point(dx, dy)
      })
      if (onClick) label.addEventListener('click', onClick)
      map.addOverLay(label)
      return {
        setPosition: (p) => label.setLngLat?.(LL(p)),
        setContent: (h) => label.setText?.(`<div style="${style}">${h}</div>`),
        destroy: () => map.removeOverLay(label)
      }
    },

    addCircle(pt, r, o = {}) {
      const c = new T.Circle(LL(pt), r, lineStyle(o))
      map.addOverLay(c)
      return c
    },

    addPolygon(pts, o = {}) {
      const c = new T.Polygon(pts.map(LL), lineStyle(o))
      map.addOverLay(c)
      return c
    },

    addPolyline(pts, o = {}) {
      const l = new T.Polyline(pts.map(LL), lineStyle(o))
      map.addOverLay(l)
      return {
        setPath: (ps) => l.setLngLats?.(ps.map(LL)),
        destroy: () => map.removeOverLay(l)
      }
    },

    remove(o) {
      if (!o) return
      if (o.destroy) o.destroy()
      else map.removeOverLay(o)
    },

    flyTo(pt, zoom) {
      map.panTo(LL(pt), (zoom ?? 12) + ZOOM_ADJ.tdt)
    },

    setViewport(pts) {
      // 天地图无 fitView:按包围盒启发式取中心与级别
      const ns = pts.map(toNative.tdt)
      const lngs = ns.map((p) => p.lng)
      const lats = ns.map((p) => p.lat)
      const cLng = (Math.min(...lngs) + Math.max(...lngs)) / 2
      const cLat = (Math.min(...lats) + Math.max(...lats)) / 2
      const span = Math.max(
        Math.max(...lngs) - Math.min(...lngs),
        (Math.max(...lats) - Math.min(...lats)) * 1.25
      ) || 0.01
      const zoom = span > 1 ? 8 : span > .5 ? 9 : span > .25 ? 10 : span > .12 ? 11
        : span > .05 ? 12 : span > .02 ? 13 : span > .01 ? 14 : span > .005 ? 15 : 16
      map.centerAndZoom(new T.LngLat(cLng, cLat), zoom)
    },

    onClick(cb) {
      map.addEventListener('click', (e) => cb(toData.tdt(e.lnglat.lng, e.lnglat.lat)))
    },

    /** 容器像素 → BD-09 业务坐标(拖放布防等 DOM 交互换算) */
    toData(x, y) {
      const g = map.containerToLngLat(new T.Point(x, y))
      return g ? toData.tdt(g.getLng(), g.getLat()) : null
    },

    /** 容器尺寸变化后强制重算画布(天地图 checkResize + resize 兜底) */
    resize() {
      try { map.checkResize?.() || map.resize?.() } catch (e) { /* 引擎无此方法时走兜底 */ }
      window.dispatchEvent(new Event('resize'))
    },

    destroy() {
      map.destroy?.()
    }
  }

  const tdtDraw = domDrawSource(el, (x, y) => {
    const g = map.containerToLngLat(new T.Point(x, y))
    return toData.tdt(g.getLng(), g.getLat())
  })
  tdtDraw.lockDblclickZoom = (lock) => {
    try { lock ? map.disableDoubleClickZoom?.() : map.enableDoubleClickZoom?.() } catch (e) { /* 无此方法时忽略 */ }
  }
  withDraw(api, tdtDraw)

  return api
}
