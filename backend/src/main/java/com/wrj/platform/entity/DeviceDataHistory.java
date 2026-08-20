package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 设备数据历史:解析后的字段值留痕(每条一帧),供地图设备详情/传感面板绘制趋势 */
@Entity
@Table(name = "device_data_history")
public class DeviceDataHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long deviceId;

    @Column(length = 64)
    private String deviceCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Device.Category category;

    /** 解析后字段 {"temperature":22.5,"humidity":55} */
    @Column(length = 4000)
    private String fieldsJson;

    @Column(nullable = false)
    private LocalDateTime ts = LocalDateTime.now();

    public DeviceDataHistory() {
    }

    public DeviceDataHistory(Long deviceId, String deviceCode, Device.Category category, String fieldsJson) {
        this.deviceId = deviceId;
        this.deviceCode = deviceCode;
        this.category = category;
        this.fieldsJson = fieldsJson;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }

    public Device.Category getCategory() { return category; }
    public void setCategory(Device.Category category) { this.category = category; }

    public String getFieldsJson() { return fieldsJson; }
    public void setFieldsJson(String fieldsJson) { this.fieldsJson = fieldsJson; }

    public LocalDateTime getTs() { return ts; }
    public void setTs(LocalDateTime ts) { this.ts = ts; }
}
