package com.wrj.platform.repository;

import com.wrj.platform.entity.GeoFence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GeoFenceRepository extends JpaRepository<GeoFence, Long> {

    List<GeoFence> findByEnabledTrue();

    /**
     * PostGIS 空间包含查询(入参为 WGS-84 经纬度):
     * - 圆形围栏:ST_DWithin(geography 米制)判断中心距 ≤ radius
     * - 多边形围栏:ST_Contains 判断点在面内
     * - 线状围栏:走廊语义,ST_DWithin 判断到线距离 ≤ radius(缓冲宽度)
     * - 复合围栏(MULTI):部件已面化为 MultiPolygon,同多边形走 ST_Contains
     */
    @Query(value = "SELECT f.id FROM geo_fence f WHERE f.enabled = TRUE AND (" +
            " (f.shape IN ('CIRCLE', 'LINE') AND f.radius IS NOT NULL AND f.geom IS NOT NULL AND " +
            "   ST_DWithin(f.geom::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, f.radius)) " +
            " OR (f.shape IN ('POLYGON', 'MULTI') AND f.geom IS NOT NULL AND " +
            "   ST_Contains(f.geom, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)))" +
            ")", nativeQuery = true)
    List<Long> findContainingFenceIds(@Param("lng") double lng, @Param("lat") double lat);
}
