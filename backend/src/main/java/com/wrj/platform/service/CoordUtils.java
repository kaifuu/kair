package com.wrj.platform.service;

/**
 * 坐标系互转工具:BD-09(百度)/ GCJ-02(高德,火星坐标)/ WGS-84(国际标准,天地图)。
 * 与前端 src/utils/coord.js 算法一致,保证前后端转换结果一致。
 * 数据库 geometry 统一存 WGS-84(SRID 4326),接口边界保持 BD-09 业务坐标。
 */
public final class CoordUtils {

    private CoordUtils() {
    }

    private static final double PI = 3.14159265358979324;
    private static final double A = 6378245.0;              // 长半轴
    private static final double EE = 0.00669342162296594323; // 偏心率平方

    public static double[] bd09ToGcj02(double bdLng, double bdLat) {
        double x = bdLng - 0.0065;
        double y = bdLat - 0.006;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * PI * 3000.0 / 180.0);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * PI * 3000.0 / 180.0);
        return new double[]{z * Math.cos(theta), z * Math.sin(theta)};
    }

    public static double[] gcj02ToBd09(double ggLng, double ggLat) {
        double x = ggLng;
        double y = ggLat;
        double z = Math.sqrt(x * x + y * y) + 0.00002 * Math.sin(y * PI * 3000.0 / 180.0);
        double theta = Math.atan2(y, x) + 0.000003 * Math.cos(x * PI * 3000.0 / 180.0);
        return new double[]{z * Math.cos(theta) + 0.0065, z * Math.sin(theta) + 0.006};
    }

    public static double[] wgs84ToGcj02(double wgLng, double wgLat) {
        if (outOfChina(wgLng, wgLat)) {
            return new double[]{wgLng, wgLat};
        }
        double dLat = transformLat(wgLng - 105.0, wgLat - 35.0);
        double dLng = transformLng(wgLng - 105.0, wgLat - 35.0);
        double radLat = wgLat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLng = (dLng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[]{wgLng + dLng, wgLat + dLat};
    }

    /** GCJ-02 → WGS-84(一阶逆逼近,精度约 1-2m,满足展示与围栏判定) */
    public static double[] gcj02ToWgs84(double ggLng, double ggLat) {
        if (outOfChina(ggLng, ggLat)) {
            return new double[]{ggLng, ggLat};
        }
        double[] wgs = wgs84ToGcj02(ggLng, ggLat);
        double dLng = ggLng - wgs[0];
        double dLat = ggLat - wgs[1];
        // 两轮迭代收敛
        wgs = wgs84ToGcj02(ggLng + dLng, ggLat + dLat);
        return new double[]{ggLng + (ggLng - wgs[0]), ggLat + (ggLat - wgs[1])};
    }

    public static double[] bd09ToWgs84(double bdLng, double bdLat) {
        double[] gcj = bd09ToGcj02(bdLng, bdLat);
        return gcj02ToWgs84(gcj[0], gcj[1]);
    }

    public static double[] wgs84ToBd09(double wgLng, double wgLat) {
        double[] gcj = wgs84ToGcj02(wgLng, wgLat);
        return gcj02ToBd09(gcj[0], gcj[1]);
    }

    private static boolean outOfChina(double lng, double lat) {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }
}
