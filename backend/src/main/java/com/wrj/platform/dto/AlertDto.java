package com.wrj.platform.dto;

/** 告警 DTO(避免懒加载序列化问题) */
public class AlertDto {

    private Long id;
    private String type;
    private String level;
    private Long droneId;
    private String droneCode;
    private String taskName;
    private String message;
    private Double lng;
    private Double lat;
    private Double altitude;
    private boolean handled;
    private String handler;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public Long getDroneId() { return droneId; }
    public void setDroneId(Long droneId) { this.droneId = droneId; }

    public String getDroneCode() { return droneCode; }
    public void setDroneCode(String droneCode) { this.droneCode = droneCode; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getAltitude() { return altitude; }
    public void setAltitude(Double altitude) { this.altitude = altitude; }

    public boolean isHandled() { return handled; }
    public void setHandled(boolean handled) { this.handled = handled; }

    public String getHandler() { return handler; }
    public void setHandler(String handler) { this.handler = handler; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
