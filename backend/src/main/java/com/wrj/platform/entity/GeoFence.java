package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 电子围栏(禁飞区/限飞区/作业区) */
@Entity
@Table(name = "geo_fence")
public class GeoFence {

    public enum Type { NO_FLY, LIMIT, WORK }
    public enum Shape { POLYGON, CIRCLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    private Type type = Type.NO_FLY;

    @Enumerated(EnumType.STRING)
    private Shape shape = Shape.POLYGON;

    /** 多边形顶点 [{lng,lat},...];圆形时为中心点 */
    @Column(length = 4000)
    private String pointsJson;

    /** 圆形半径 m(仅 CIRCLE) */
    private Double radius;

    private Double maxAltitude = 0.0;    // 限高(禁飞区=0)
    private Boolean enabled = true;
    private String remark;

    private LocalDateTime createdAt = LocalDateTime.now();

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

    public Double getMaxAltitude() { return maxAltitude; }
    public void setMaxAltitude(Double maxAltitude) { this.maxAltitude = maxAltitude; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
