package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 无人机档案 */
@Entity
@Table(name = "drone")
public class Drone {

    public enum Status { IDLE, FLYING, CHARGING, MAINTENANCE, OFFLINE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;              // 机身编号

    private String model;             // 机型,如 DJI M350 RTK
    private String manufacturer;      // 制造商
    private String category;          // 用途分类:航拍/巡检/测绘/物流/农业

    @Enumerated(EnumType.STRING)
    private Status status = Status.IDLE;

    /** 绑定飞手 */
    @ManyToOne
    @JoinColumn(name = "pilot_id")
    private Pilot pilot;

    private Double homeLng;           // 归航点经度
    private Double homeLat;           // 归航点纬度
    private Double maxAltitude = 500.0;   // 最大航高 m
    private Double maxEndurance = 55.0;   // 最大续航 min
    private Double totalFlightHours = 0.0; // 累计飞行小时

    private LocalDateTime purchaseDate;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Drone() {
    }

    public Drone(String code, String model, String manufacturer, String category,
                 Double homeLng, Double homeLat) {
        this.code = code;
        this.model = model;
        this.manufacturer = manufacturer;
        this.category = category;
        this.homeLng = homeLng;
        this.homeLat = homeLat;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Pilot getPilot() { return pilot; }
    public void setPilot(Pilot pilot) { this.pilot = pilot; }

    public Double getHomeLng() { return homeLng; }
    public void setHomeLng(Double homeLng) { this.homeLng = homeLng; }

    public Double getHomeLat() { return homeLat; }
    public void setHomeLat(Double homeLat) { this.homeLat = homeLat; }

    public Double getMaxAltitude() { return maxAltitude; }
    public void setMaxAltitude(Double maxAltitude) { this.maxAltitude = maxAltitude; }

    public Double getMaxEndurance() { return maxEndurance; }
    public void setMaxEndurance(Double maxEndurance) { this.maxEndurance = maxEndurance; }

    public Double getTotalFlightHours() { return totalFlightHours; }
    public void setTotalFlightHours(Double totalFlightHours) { this.totalFlightHours = totalFlightHours; }

    public LocalDateTime getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDateTime purchaseDate) { this.purchaseDate = purchaseDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
