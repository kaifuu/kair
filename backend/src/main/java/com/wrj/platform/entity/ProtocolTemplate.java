package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 设备接入协议模板:
 * - transport:接入方式(TCP 标准帧 / RS232 / RS485 经 DTU 透传 / PLC Modbus TCP)
 * - frameFormat:帧格式(TLV=tag/length/value、FIXED=定长偏移切分、MODBUS=寄存器映射)
 * - rulesJson:TLV 规则 [{"tag":1,"field":"lng","type":"uint32","scale":1.0E-6,"unit":"deg","radix":10}]
 * - configJson:帧结构参数,按 frameFormat 取用
 *   TLV   {"tagLen":1,"lenLen":2,"littleEndian":false}
 *   FIXED {"fields":[{"offset":0,"len":2,"field":"temperature","type":"int16","scale":0.1,"unit":"℃","radix":10}]}
 *   MODBUS{"unitId":1,"regMap":[{"reg":0,"count":2,"field":"pressure","type":"int32","scale":0.1,"unit":"kPa"}]}
 * Netty 网关按设备绑定模板的 frameFormat 选择解析引擎,radix 支持 2/8/10/16 进制数据拆分。
 */
@Entity
@Table(name = "protocol_template")
public class ProtocolTemplate {

    /** 接入方式 */
    public enum Transport { TCP, UDP, RS232, RS485, MODBUS_TCP }

    /** 帧格式/解析引擎 */
    public enum FrameFormat { TLV, FIXED, MODBUS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Transport transport = Transport.TCP;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private FrameFormat frameFormat = FrameFormat.TLV;

    /** TLV 规则列表 JSON */
    @Column(length = 8000)
    private String rulesJson = "[]";

    /** 帧结构参数 JSON(偏移表/寄存器映射/TLV 头参数) */
    @Column(length = 8000)
    private String configJson = "{}";

    private LocalDateTime createdAt = LocalDateTime.now();

    public ProtocolTemplate() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Transport getTransport() { return transport; }
    public void setTransport(Transport transport) { this.transport = transport; }

    public FrameFormat getFrameFormat() { return frameFormat; }
    public void setFrameFormat(FrameFormat frameFormat) { this.frameFormat = frameFormat; }

    public String getRulesJson() { return rulesJson; }
    public void setRulesJson(String rulesJson) { this.rulesJson = rulesJson; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
