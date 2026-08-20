package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 飞行任务 */
@Entity
@Table(name = "flight_task")
public class FlightTask {

    public enum Status { PENDING, FLYING, COMPLETED, ABORTED }
    public enum Approval { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;                 // 任务名称

    @Column(length = 255)
    private String description;

    @ManyToOne(optional = false)
    @JoinColumn(name = "device_id")
    private Device device;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pilot_id")
    private Pilot pilot;

    /** 航线:[{lng,lat,alt}, ...] */
    @Column(length = 4000)
    private String routeJson;

    private Double plannedAltitude = 120.0;   // 计划航高 m
    private Double plannedDuration = 20.0;    // 计划时长 min

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    private Approval approval = Approval.PENDING;

    private LocalDateTime createdAt = LocalDateTime.now();

    public FlightTask() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Device getDevice() { return device; }
    public void setDevice(Device device) { this.device = device; }

    public Pilot getPilot() { return pilot; }
    public void setPilot(Pilot pilot) { this.pilot = pilot; }

    public String getRouteJson() { return routeJson; }
    public void setRouteJson(String routeJson) { this.routeJson = routeJson; }

    public Double getPlannedAltitude() { return plannedAltitude; }
    public void setPlannedAltitude(Double plannedAltitude) { this.plannedAltitude = plannedAltitude; }

    public Double getPlannedDuration() { return plannedDuration; }
    public void setPlannedDuration(Double plannedDuration) { this.plannedDuration = plannedDuration; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Approval getApproval() { return approval; }
    public void setApproval(Approval approval) { this.approval = approval; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
