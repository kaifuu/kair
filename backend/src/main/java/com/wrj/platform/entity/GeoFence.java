package com.wrj.platform.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wrj.platform.service.FenceGeometry;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Geometry;

import java.time.LocalDateTime;

/**
 * 电子围栏(禁飞区/限飞区/作业区)。
 * 点线面双表示:
 * - pointsJson:BD-09 业务坐标(接口层视图,前端直接渲染,兼容既有页面)
 * - geom:PostGIS geometry(WGS-84, SRID 4326,权威空间数据源,支撑 ST_Contains/ST_DWithin 与 GiST 索引)
 * 持久化前由实体回调从 pointsJson 自动构建 geom,保证两者一致;圆形=点+radius,线=LineString,多边形=Polygon。
 */
@Entity
@Table(name = "geo_fence")
public class GeoFence {

    public enum Type { NO_FLY, LIMIT, WORK }

    /** CIRCLE=点(中心+radius)、LINE=线(航线/走廊)、POLYGON=面、MULTI=复合(一个围栏多个区域) */
    public enum Shape { POLYGON, CIRCLE, LINE, MULTI }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    /**
     * 注意:字段一律不带默认值初始化 —— Jackson 反序列化部分字段 PUT(如 {enabled})时,
     * 初始化器会把 shape/type 重置为默认值,悄悄污染未提交的字段;默认值由 Controller.create 统一兜底。
     */
    @Enumerated(EnumType.STRING)
    private Type type;

    @Enumerated(EnumType.STRING)
    private Shape shape;

    /**
     * 多边形顶点/线节点 [{lng,lat},...];圆形时为中心点(BD-09);
     * MULTI 复合围栏存部件数组 [{shape,radius,points:[{lng,lat},...]},...](一个围栏多个区域,共用开关)
     */
    @Column(length = 8000)
    private String pointsJson;

    /** 圆形半径 m(仅 CIRCLE) */
    private Double radius;

    /** PostGIS 几何(WGS-84 4326);仅服务端空间查询使用,不参与 JSON 序列化 */
    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "geom", columnDefinition = "geometry(Geometry,4326)")
    @JsonIgnore
    private Geometry geom;

    private Double maxAltitude;          // 限高(禁飞区=0)
    private Boolean enabled;
    private String remark;

    private LocalDateTime createdAt = LocalDateTime.now();

    /** 持久化前从 pointsJson(BD-09)构建 WGS-84 几何,双写保持一致 */
    @PrePersist
    @PreUpdate
    private void syncGeometry() {
        this.geom = FenceGeometry.fromPointsJson(this.pointsJson, this.shape);
    }

    public GeoFence() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public Shape getShape() { return shape; }
    public void setShape(Shape shape) { this.shape = shape; }

    public String getPointsJson() { return pointsJson; }
    public void setPointsJson(String pointsJson) { this.pointsJson = pointsJson; }

    public Double getRadius() { return radius; }
    public void setRadius(Double radius) { this.radius = radius; }

    @JsonIgnore
    public Geometry getGeom() { return geom; }
    public void setGeom(Geometry geom) { this.geom = geom; }

    public Double getMaxAltitude() { return maxAltitude; }
    public void setMaxAltitude(Double maxAltitude) { this.maxAltitude = maxAltitude; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
