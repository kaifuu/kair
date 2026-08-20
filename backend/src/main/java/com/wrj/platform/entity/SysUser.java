package com.wrj.platform.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 系统用户(人员管理) */
@Entity
@Table(name = "sys_user")
public class SysUser {

    public enum Status { ENABLED, DISABLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    @JsonIgnore
    @Column(nullable = false, length = 100)
    private String password;          // BCrypt

    @Column(length = 32)
    private String nickname;

    @Column(length = 20)
    private String phone;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private SysRole role;

    private Long orgId;               // 平列 id,不建实体关联
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ENABLED;

    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt = LocalDateTime.now();

    public SysUser() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public SysRole getRole() { return role; }
    public void setRole(SysRole role) { this.role = role; }

    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
