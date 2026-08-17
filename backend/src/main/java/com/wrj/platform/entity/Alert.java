package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 告警事件 */
@Entity
@Table(name = "alert")
public class Alert {

    public enum Level { INFO, WARNING, CRITICAL }
    public enum Type {
        GEOFENCE_BREACH,     // 闯入禁飞区
        ALTITUDE_EXCEED,     // 超高
        LOW_BATTERY,         // 低电量
        SIGNAL_LOST,         // 失联
        NO_LICENSE,          // 黑飞(无任务飞行)
        TASK_OVERDUE         // 超时未归
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Type type;

    @Enumerated(EnumType.STRING)
    private Level level;

    @ManyToOne
    @JoinColumn(name = "drone_id")
    private Drone drone;

    @ManyToOne
    @JoinColumn(name = "task_id")
    private FlightTask task;

    @Column(length = 255)
    private String message;

    private Double lng;
    private Double lat;
    private Double altitude;

    private Boolean handled = false;
    private String handler;
    private LocalDateTime handleTime;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Alert() {
    }

    public Alert(Type type, Level level, Drone drone, FlightTask task, String message,
                 Double lng, Double lat, Double altitude) {
        this.type = type;
        this.level = level;
        this.drone = drone;
        this.task = task;
        this.message = message;
        this.lng = lng;
        this.lat = lat;
        this.altitude = altitude;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public Level getLevel() { return level; }
    public void setLevel(Level level) { this.level = level; }

    public Drone getDrone() { return drone; }
    public void setDrone(Drone drone) { this.drone = drone; }

    public FlightTask getTask() { return task; }
    public void setTask(FlightTask task) { this.task = task; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getAltitude() { return altitude; }
    public void setAltitude(Double altitude) { this.altitude = altitude; }

    public Boolean getHandled() { return handled; }
    public void setHandled(Boolean handled) { this.handled = handled; }

    public String getHandler() { return handler; }
    public void setHandler(String handler) { this.handler = handler; }

    public LocalDateTime getHandleTime() { return handleTime; }
    public void setHandleTime(LocalDateTime handleTime) { this.handleTime = handleTime; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
