package com.wrj.platform.dto;

import java.util.List;
import java.util.Map;

/** 实时遥测数据(单架无人机一个 tick) */
public class TelemetryDto {

    private Long droneId;
    private String droneCode;
    private String model;
    private String status;        // flying | idle | ...
    private Long taskId;
    private String taskName;
    private String pilotName;

    private double lng;
    private double lat;
    private double altitude;      // m
    private double speed;         // m/s
    private double heading;       // 航向角 0-360
    private double battery;       // 0-100
    private int satellites;

    /** 最近轨迹点(前端画线用) */
    private List<Map<String, Double>> track;

    public Long getDroneId() { return droneId; }
    public void setDroneId(Long droneId) { this.droneId = droneId; }

    public String getDroneCode() { return droneCode; }
    public void setDroneCode(String droneCode) { this.droneCode = droneCode; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public String getPilotName() { return pilotName; }
    public void setPilotName(String pilotName) { this.pilotName = pilotName; }

    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getAltitude() { return altitude; }
    public void setAltitude(double altitude) { this.altitude = altitude; }

    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }

    public double getHeading() { return heading; }
    public void setHeading(double heading) { this.heading = heading; }

    public double getBattery() { return battery; }
    public void setBattery(double battery) { this.battery = battery; }

    public int getSatellites() { return satellites; }
    public void setSatellites(int satellites) { this.satellites = satellites; }

    public List<Map<String, Double>> getTrack() { return track; }
    public void setTrack(List<Map<String, Double>> track) { this.track = track; }
}
