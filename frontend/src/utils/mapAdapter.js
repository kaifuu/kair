/**
 * 统一地图适配层
 * 对外暴露一致的地图 API(addMarker/addCircle/addPolygon/addLabel/flyTo...),
 * 内部按当前提供商分发给 百度 GL / 高德 JS API 2.0 / 天地图 4.0。
 *
 * 坐标约定:业务侧一律传/收 BD-09({lng,lat}),
 * 适配层负责与 GCJ-02(高德)/ WGS-84(天地图)互转,数据模型不感知差异。
 */
import { loadMapSdk, getProviderId } from './mapProviders'
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
  const pid = getProviderId()
  const sdk = await loadMapSdk(pid)
  if (pid === 'amap') return amapFacade(sdk, el, opts)
  if (pid === 'tdt') return tdtFacade(sdk, el, opts)
  return baiduFacade(sdk, el, opts)
}

/* ============================== 百度 GL ============================== */

function baiduFacade(BMapGL, el, opts) {
  const P = (p) => new BMapGL.Point(p.lng, p.lat)
  const map = new BMapGL.Map(el, { enableMapClick: false })
  map.centerAndZoom(P(opts.center || { lng: 116.404, lat: 39.925 }), opts.zoom || 12)
  map.enableScrollWheelZoom(true)
  if (opts.customStyle) map.setMapStyleV2(DARK_MAP_STYLE)

  const api = {
    provider: 'baidu',
    raw: map,

    applyView3d(on) {
      map.setTilt(on ? 73 : 0)
      map.setHeading(on ? 64.5 : 0)
    },

    addMarker(pt, m = {}) {
      const size = m.size || 52
      const mkIcon = (url) => new BMapGL.Icon(url, {
        size: new BMapGL.Size(size, size),
        anchor: new BMapGL.Size(size / 2, size / 2)
      })
      const marker = new BMapGL.Marker(P(pt), { icon: mkIcon(m.svg || droneSvg(m.heading || 0)) })
      if (m.onClick) marker.addEventListener('click', m.onClick)
      map.addOverlay(marker)
      return {
        update(next) {
          if (!m.svg && next.heading != null) marker.setIcon(mkIcon(droneSvg(next.heading)))
          if (next.lng != null) marker.setPosition(P(next))
        },
        destroy() { map.removeOverlay(marker) }
      }
    },

    addLabel(pt, html, { dx = 0, dy = 0, css = {} } = {}) {
      const label = new BMapGL.Label(html, {
        position: P(pt),
        offset: new BMapGL.Size(dx, dy)
      })
      label.setStyle(css)
      map.addOverlay(label)
      return {
        setPosition: (p) => label.setPosition(P(p)),
        setContent: (h) => label.setContent(h),
        destroy: () => map.removeOverlay(label)
      }
    },

    addCircle(pt, r, o = {}) {
      const c = new BMapGL.Circle(P(pt), r, polyStyle(o))
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
      if (o.destroy) o.destroy()
      else map.removeOverlay(o)
    },

    flyTo(pt, zoom) {
      map.flyTo(P(pt), zoom ?? 12)
    },

    setViewport(pts) {
      map.setViewport(pts.map(P))
    },

    onClick(cb) {
      map.addEventListener('click', (e) => cb({ lng: e.point.lng, lat: e.point.lat }))
    },

    destroy() {
      map.destroy?.()
    }
  }

  if (opts.view3d) {
    api.applyView3d(true)
    map.addControl(new BMapGL.NavigationControl3D({
      anchor: window.BMAP_ANCHOR_BOTTOM_RIGHT,
      offset: new BMapGL.Size(16, 16)
    }))
  }
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

  const api = {
    provider: 'amap',
    raw: map,

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
          if (!m.svg && next.heading != null) marker.setIcon(mkIcon(droneSvg(next.heading)))
          if (next.lng != null) marker.setPosition(LL(next))
        },
        destroy() { map.remove(marker) }
      }
    },

    addLabel(pt, html, { dx = 0, dy = 0, css = {} } = {}) {
      const style = `${cssText(css)};pointer-events:none`
      const marker = new AMap.Marker({
        position: LL(pt),
        anchor: 'top-left',
        offset: new AMap.Pixel(dx, dy),
        content: `<div style="${style}">${html}</div>`,
        cursor: 'default'
      })
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

    destroy() {
      map.destroy?.()
    }
  }

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
  map.centerAndZoom(LL(opts.center || { lng: 116.404, lat: 39.925 }), (opts.zoom || 12) + ZOOM_ADJ.tdt)
  map.enableScrollWheelZoom()

  const api = {
    provider: 'tdt',
    raw: map,

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
          if (!m.svg && next.heading != null) marker.setIcon(mkIcon(droneSvg(next.heading)))
          if (next.lng != null) marker.setLngLat(LL(next))
        },
        destroy() { map.removeOverLay(marker) }
      }
    },

    addLabel(pt, html, { dx = 0, dy = 0, css = {} } = {}) {
      const style = `${cssText(css)};pointer-events:none`
      const label = new T.Label({
        text: `<div style="${style}">${html}</div>`,
        position: LL(pt),
        offset: new T.Point(dx, dy)
      })
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

    destroy() {
      map.destroy?.()
    }
  }

  return api
}
