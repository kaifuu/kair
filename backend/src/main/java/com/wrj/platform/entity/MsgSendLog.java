package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 消息发送记录:每次按通道落一条,便于排障与统计 */
@Entity
@Table(name = "msg_send_log")
public class MsgSendLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 通道类型 JPUSH/UMENG/EMAIL/SMS/INAPP */
    @Column(nullable = false, length = 16)
    private String channelType;

    @Column(length = 64)
    private String channelName;

    @Column(length = 128)
    private String title;

    @Column(columnDefinition = "text")
    private String receivers;

    /** SUCCESS / FAIL */
    @Column(length = 16)
    private String status;

    @Column(length = 500)
    private String error;

    private Long costMs;

    private LocalDateTime createdAt = LocalDateTime.now();

    public MsgSendLog() {
    }

    public MsgSendLog(String channelType, String channelName, String title, String receivers,
                      String status, String error, long costMs) {
        this.channelType = channelType;
        this.channelName = channelName;
        this.title = title;
        this.receivers = receivers;
        this.status = status;
        this.error = error;
        this.costMs = costMs;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }

    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getReceivers() { return receivers; }
    public void setReceivers(String receivers) { this.receivers = receivers; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public Long getCostMs() { return costMs; }
    public void setCostMs(Long costMs) { this.costMs = costMs; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
