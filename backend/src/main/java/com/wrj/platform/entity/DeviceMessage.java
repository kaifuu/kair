package com.wrj.platform.entity;

import com.wrj.platform.gateway.BaseCodec;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 设备报文日志:Netty 网关上下行整帧留痕(HEX 含 magic/CRC),按设备/方向/帧类型查询 */
@Entity
@Table(name = "device_message", indexes = {
        @Index(name = "idx_dm_device", columnList = "deviceId, id"),
        @Index(name = "idx_dm_created", columnList = "createdAt")
})
public class DeviceMessage {

    public enum Direction { UP, DOWN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 注册前上行帧未绑定设备,可为空 */
    private Long deviceId;

    @Column(length = 64)
    private String deviceCode;

    @Enumerated(EnumType.STRING)
    private Direction direction;

    /** REGISTER / HEARTBEAT / DATA / ACK / COMMAND */
    @Column(length = 16)
    private String frameType;

    /** 整帧大写 HEX */
    @Column(length = 32000)
    private String contentHex;

    private int length;

    private Boolean ok = true;

    private LocalDateTime createdAt = LocalDateTime.now();

    public DeviceMessage() {
    }

    public DeviceMessage(Long deviceId, String deviceCode, Direction direction,
                         String frameType, byte[] frame, boolean ok) {
        this.deviceId = deviceId;
        this.deviceCode = deviceCode;
        this.direction = direction;
        this.frameType = frameType;
        this.length = frame == null ? 0 : frame.length;
        this.contentHex = BaseCodec.encode(frame, "hex");
        this.ok = ok;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }

    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }

    public String getFrameType() { return frameType; }
    public void setFrameType(String frameType) { this.frameType = frameType; }

    public String getContentHex() { return contentHex; }
    public void setContentHex(String contentHex) { this.contentHex = contentHex; }

    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }

    public Boolean getOk() { return ok; }
    public void setOk(Boolean ok) { this.ok = ok; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
