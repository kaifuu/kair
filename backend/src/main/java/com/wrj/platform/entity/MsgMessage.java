package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 站内消息:receiver 为 ALL 表示全员广播,否则为接收人用户名 */
@Entity
@Table(name = "msg_message", indexes = @Index(columnList = "receiver"))
public class MsgMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** INFO / WARNING / CRITICAL */
    @Column(length = 16)
    private String level = "INFO";

    @Column(length = 64)
    private String sender;

    @Column(nullable = false, length = 64)
    private String receiver = "ALL";

    private LocalDateTime createdAt = LocalDateTime.now();

    public MsgMessage() {
    }

    public MsgMessage(String title, String content, String level, String sender, String receiver) {
        this.title = title;
        this.content = content;
        this.level = level;
        this.sender = sender;
        this.receiver = receiver;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
