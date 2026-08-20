package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 站内消息已读记录(按用户) */
@Entity
@Table(name = "msg_read", uniqueConstraints = @UniqueConstraint(columnNames = {"messageId", "username"}))
public class MsgRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long messageId;

    @Column(nullable = false, length = 64)
    private String username;

    private LocalDateTime readAt = LocalDateTime.now();

    public MsgRead() {
    }

    public MsgRead(Long messageId, String username) {
        this.messageId = messageId;
        this.username = username;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
}
