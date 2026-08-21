package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 统一设备档案:
 * 无人机(DRONE)与物联网设备共用此表,通过 category 分类标识;
 * 无人机专有字段(飞手/归航点/航高/续航等)仅 category=DRONE 时使用。
 */
@Entity
@Table(name = "device")
public class Device {

    /** 设备分类(后 6 类为无人机反制设备,供攻防演练布防) */
    public enum Category {
        DRONE, DOCK, CAMERA, WEATHER, ADSB, GATEWAY, SENSOR,
        RADAR,          // 警戒雷达(探测)
        RADIO_DETECT,   // 无线电探测(探测)
        EO_TRACK,       // 光电跟踪(探测/锁定,配光电视窗)
        RADIO_JAM,      // 无线电压制(反制:驱离)
        LASER,          // 激光处置(反制:击落,需光电锁定)
        NET_CAPTURE     // 网捕无人机(反制:捕获)
    }

    /** ONLINE=网关在线(物联网设备),IDLE/FLYING/MAINTENANCE 主要面向无人机语义 */
    public enum Status { ONLINE, OFFLINE, IDLE, FLYING, MAINTENANCE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;              // 设备编码(无人机为机身编号)

    private String name;              // 设备名称

    // 注意:字段不初始化默认值 —— 否则部分 PUT(如仅 {icon})经 Jackson 反序列化会把
    // 默认值当"已提交字段"覆盖存量(category 被重置为 DRONE)。默认值由 DeviceController.create 兜底。
    @Enumerated(EnumType.STRING)
    private Category category;

    private String usage;             // 用途分类(无人机):航拍/巡检/测绘/物流/农业

    private String manufacturer;
    private String model;

    @Enumerated(EnumType.STRING)
    private Status status;

    /** 绑定的协议模板(TLV/FIXED/MODBUS,Netty 接入解析用) */
    @ManyToOne
    @JoinColumn(name = "protocol_id")
    private ProtocolTemplate protocol;

    /** Modbus TCP 从站单元号(仅 MODBUS_TCP 接入设备使用,网关按 unitId 寻址设备) */
    private Integer modbusUnitId;

    @Column(nullable = false, length = 64)
    private String secret = "";       // 设备密钥(TCP/DTU 注册鉴权)

    /** 虚拟设备:由 FlightSimulator 驱动,不接入 Netty 网关 */
    private Boolean virtual = false;

    /** 视频流地址(HLS m3u8,摄像头类设备;前端经 /api/video/proxy 代理播放) */
    @Column(length = 512)
    private String videoUrl;

    /** 扫描/作用范围 m(反制设备:探测类=探测半径,反制类=有效作用半径) */
    private Double scanRange;

    /** 自定义地图图标(http/data URL,空=按分类默认 SVG;监控地图按此渲染) */
    @Column(length = 300000)
    private String icon;

    private LocalDateTime lastOnlineAt;
    private String lastIp;
    private Boolean enabled = true;

    private Long orgId;               // 归属组织(平列 id,不建实体关联)

    // ---- 无人机专有(可空) ----
    @ManyToOne
    @JoinColumn(name = "pilot_id")
    private Pilot pilot;              // 绑定飞手

    private Double homeLng;
    private Double homeLat;
    private Double maxAltitude = 500.0;
    private Double maxEndurance = 55.0;
    private Double totalFlightHours = 0.0;
    private LocalDateTime purchaseDate;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Device() {
    }

    public Device(String code, String name, Category category, String model, String manufacturer) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.model = model;
        this.manufacturer = manufacturer;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public String getUsage() { return usage; }
    public void setUsage(String usage) { this.usage = usage; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public ProtocolTemplate getProtocol() { return protocol; }
    public void setProtocol(ProtocolTemplate protocol) { this.protocol = protocol; }

    public Integer getModbusUnitId() { return modbusUnitId; }
    public void setModbusUnitId(Integer modbusUnitId) { this.modbusUnitId = modbusUnitId; }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public Boolean getVirtual() { return virtual; }
    public void setVirtual(Boolean virtual) { this.virtual = virtual; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public Double getScanRange() { return scanRange; }
    public void setScanRange(Double scanRange) { this.scanRange = scanRange; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public LocalDateTime getLastOnlineAt() { return lastOnlineAt; }
    public void setLastOnlineAt(LocalDateTime lastOnlineAt) { this.lastOnlineAt = lastOnlineAt; }

    public String getLastIp() { return lastIp; }
    public void setLastIp(String lastIp) { this.lastIp = lastIp; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }

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
