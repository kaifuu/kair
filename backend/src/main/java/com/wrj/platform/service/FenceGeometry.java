package com.wrj.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wrj.platform.entity.GeoFence;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.union.CascadedPolygonUnion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 围栏几何构建:pointsJson(BD-09 业务坐标)→ JTS Geometry(WGS-84, SRID 4326)。
 * 由 GeoFence 实体在持久化前回调,保证 geometry 与 pointsJson 双写一致;
 * geometry 为 PostGIS 空间查询(ST_Contains/ST_DWithin/GiST 索引)的权威数据源。
 *
 * pointsJson 两种格式:
 * - 单形状(兼容既有数据):[{lng,lat},...],按 shape 构建 圆点/线/多边形;
 * - 复合(MULTI,一个围栏多个区域):[{shape,radius,points:[{lng,lat},...]},...],
 *   每个部件面化(圆→正 72 边形、线→走廊带、多边形→面)后合并为 MultiPolygon,统一走 ST_Contains。
 */
public final class FenceGeometry {

    private static final Logger log = LoggerFactory.getLogger(FenceGeometry.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final GeometryFactory GF = new GeometryFactory();

    /** 米/度换算:纬向恒定,经向随纬度收缩 */
    private static final double LAT_M_PER_DEG = 111320.0;

    private FenceGeometry() {
    }

    /** 把 BD-09 点串构建为 WGS-84 几何:圆→点(配合 radius)、线→LineString、多边形→Polygon、复合→MultiPolygon */
    public static Geometry fromPointsJson(String pointsJson, GeoFence.Shape shape) {
        List<Map<String, Object>> raw = parseMaps(pointsJson);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            if (raw.get(0).containsKey("points")) {
                return fromParts(raw);   // 复合格式:元素带 points 部件数组
            }
            List<Coordinate> wgs = toWgs84Coordinates(raw);
            if (wgs.isEmpty()) {
                return null;
            }
            Geometry geom = switch (shape) {
                case CIRCLE -> GF.createPoint(wgs.get(0));
                case LINE -> wgs.size() >= 2 ? GF.createLineString(wgs.toArray(new Coordinate[0])) : null;
                default -> toPolygon(wgs);
            };
            if (geom != null) {
                geom.setSRID(4326);
            }
            return geom;
        } catch (Exception e) {
            log.warn("Fence geometry build failed: {}", e.getMessage());
            return null;
        }
    }

    /** 圆形围栏中心点转 WGS-84(供单点查询场景) */
    public static double[] centerWgs84(String pointsJson) {
        List<Coordinate> wgs = toWgs84Coordinates(parseMaps(pointsJson));
        return wgs.isEmpty() ? null : new double[]{wgs.get(0).x, wgs.get(0).y};
    }

    /** 复合围栏:每个部件面化后合并(MultiPolygon/并集),空间查询统一 ST_Contains */
    private static Geometry fromParts(List<Map<String, Object>> parts) {
        List<Geometry> areas = new ArrayList<>();
        for (Map<String, Object> part : parts) {
            String partShape = String.valueOf(part.get("shape"));
            Double radius = part.get("radius") == null ? null : ((Number) part.get("radius")).doubleValue();
            List<Coordinate> pts = toWgs84Coordinates(part.get("points"));
            Geometry area = switch (partShape) {
                case "CIRCLE" -> (radius != null && !pts.isEmpty()) ? ringToPolygon(circleRing(pts.get(0), radius)) : null;
                case "LINE" -> (radius != null && pts.size() >= 2) ? corridorPolygon(pts, radius) : null;
                default -> pts.size() >= 3 ? toPolygon(pts) : null;
            };
            if (area != null && !area.isEmpty()) {
                areas.add(area);
            }
        }
        if (areas.isEmpty()) {
            return null;
        }
        Geometry merged = areas.size() == 1 ? areas.get(0) : CascadedPolygonUnion.union(areas);
        merged.setSRID(4326);
        return merged;
    }

    /** 圆心 + 半径 → 正 72 边形环(米制半径按当地经纬尺度换算为度) */
    private static List<Coordinate> circleRing(Coordinate center, double radiusM) {
        int n = 72;
        double lngM = Math.max(1, LAT_M_PER_DEG * Math.cos(Math.toRadians(center.y)));
        List<Coordinate> ring = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) {
            double a = 2 * Math.PI * i / n;
            ring.add(new Coordinate(
                    round6(center.x + Math.cos(a) * radiusM / lngM),
                    round6(center.y + Math.sin(a) * radiusM / LAT_M_PER_DEG)));
        }
        return ring;
    }

    /** 折线 + 走廊宽 → 走廊面:逐段矩形(端头外扩 radius 衔接)并集 */
    private static Geometry corridorPolygon(List<Coordinate> line, double radiusM) {
        List<Geometry> rects = new ArrayList<>();
        for (int i = 0; i + 1 < line.size(); i++) {
            Coordinate a = line.get(i), b = line.get(i + 1);
            double lngM = Math.max(1, LAT_M_PER_DEG * Math.cos(Math.toRadians((a.y + b.y) / 2)));
            double dxm = (b.x - a.x) * lngM, dym = (b.y - a.y) * LAT_M_PER_DEG;
            double len = Math.hypot(dxm, dym);
            if (len < 0.01) {
                continue;
            }
            double uxm = dxm / len, uym = dym / len;       // 段方向单位向量(米制)
            double nxm = -uym, nym = uxm;                  // 法向(米制)
            double ox = radiusM / lngM, oy = radiusM / LAT_M_PER_DEG;
            Coordinate[] rect = {
                    offset(a, -uxm * ox - nxm * ox, -uym * oy - nym * oy),
                    offset(b, uxm * ox - nxm * ox, uym * oy - nym * oy),
                    offset(b, uxm * ox + nxm * ox, uym * oy + nym * oy),
                    offset(a, -uxm * ox + nxm * ox, -uym * oy + nym * oy),
                    null};
            rect[4] = new Coordinate(rect[0].x, rect[0].y); // 闭合
            rects.add(GF.createPolygon(rect));
        }
        return rects.isEmpty() ? null : CascadedPolygonUnion.union(rects);
    }

    private static Coordinate offset(Coordinate p, double dLng, double dLat) {
        return new Coordinate(round6(p.x + dLng), round6(p.y + dLat));
    }

    private static Polygon ringToPolygon(List<Coordinate> ring) {
        return GF.createPolygon(ring.toArray(new Coordinate[0]));
    }

    private static Geometry toPolygon(List<Coordinate> wgs) {
        if (wgs.size() < 3) {
            return null;
        }
        List<Coordinate> ring = new ArrayList<>(wgs);
        Coordinate first = ring.get(0);
        Coordinate last = ring.get(ring.size() - 1);
        if (first.x != last.x || first.y != last.y) {
            ring.add(new Coordinate(first.x, first.y)); // JTS 要求环闭合
        }
        return GF.createPolygon(ring.toArray(new Coordinate[0]));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseMaps(String pointsJson) {
        if (pointsJson == null || pointsJson.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(pointsJson,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            log.warn("Fence points parse failed: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Coordinate> toWgs84Coordinates(Object pointsNode) {
        List<Coordinate> result = new ArrayList<>();
        if (!(pointsNode instanceof List)) {
            return result;
        }
        for (Object o : (List<Object>) pointsNode) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> p = (Map<String, Object>) o;
            Object lng = p.get("lng"), lat = p.get("lat");
            if (!(lng instanceof Number) || !(lat instanceof Number)) {
                continue;
            }
            double[] wgs = CoordUtils.bd09ToWgs84(((Number) lng).doubleValue(), ((Number) lat).doubleValue());
            result.add(new Coordinate(round6(wgs[0]), round6(wgs[1])));
        }
        return result;
    }

    private static double round6(double v) {
        return Math.round(v * 1e6) / 1e6;
    }
}
