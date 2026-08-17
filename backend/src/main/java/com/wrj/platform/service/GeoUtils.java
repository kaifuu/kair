package com.wrj.platform.service;

import java.util.List;
import java.util.Map;

/** 地理计算工具:点在多边形内判断、距离、航向角 */
public final class GeoUtils {

    private GeoUtils() {
    }

    private static final double EARTH_R = 6378137.0; // m

    /** 两点间大圆距离(米) */
    public static double distance(double lng1, double lat1, double lng2, double lat2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double dLat = radLat2 - radLat1;
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_R * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** 从点1指向点2的航向角(0-360,正北为0) */
    public static double bearing(double lng1, double lat1, double lng2, double lat2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double dLng = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dLng) * Math.cos(radLat2);
        double x = Math.cos(radLat1) * Math.sin(radLat2)
                - Math.sin(radLat1) * Math.cos(radLat2) * Math.cos(dLng);
        double deg = Math.toDegrees(Math.atan2(y, x));
        return (deg + 360.0) % 360.0;
    }

    /** 射线法:点是否在多边形内。points 元素需含 lng/lat 键 */
    public static boolean pointInPolygon(double lng, double lat, List<Map<String, Double>> points) {
        if (points == null || points.size() < 3) {
            return false;
        }
        boolean inside = false;
        int n = points.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = points.get(i).get("lng");
            double yi = points.get(i).get("lat");
            double xj = points.get(j).get("lng");
            double yj = points.get(j).get("lat");
            boolean intersect = ((yi > lat) != (yj > lat))
                    && (lng < (xj - xi) * (lat - yi) / (yj - yi) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** 沿大圆路径插值:返回从(from)到(to)按比例 t 的点 */
    public static double[] interpolate(double lng1, double lat1, double lng2, double lat2, double t) {
        return new double[]{
                lng1 + (lng2 - lng1) * t,
                lat1 + (lat2 - lat1) * t
        };
    }
}
