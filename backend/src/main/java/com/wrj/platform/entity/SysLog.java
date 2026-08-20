package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 系统日志:操作(OPERATE)/登录(LOGIN)/设备(DEVICE) 三类 */
@Entity
@Table(name = "sys_log", indexes = @Index(name = "idx_sys_log_type_time", columnList = "type, createdAt"))
public class SysLog {

    public enum Type { OPERATE, LOGIN, DEVICE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Type type;

    @Column(length = 32)
    private String username;

    @Column(length = 64)
    private String action;

    @Column(length = 1000)
    private String detail;

    @Column(length = 64)
    private String ip;

    private Boolean success = true;

    private LocalDateTime createdAt = LocalDateTime.now();

    public SysLog() {
    }

    public SysLog(Type type, String username, String action, String detail, String ip, boolean success) {
        this.type = type;
        this.username = username;
        this.action = action;
        this.detail = detail;
        this.ip = ip;
        this.success = success;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
