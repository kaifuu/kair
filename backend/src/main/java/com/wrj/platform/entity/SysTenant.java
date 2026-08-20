package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 租户 */
@Entity
@Table(name = "sys_tenant")
public class SysTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(length = 255)
    private String remark;

    private Boolean enabled = true;

    private LocalDateTime createdAt = LocalDateTime.now();

    public SysTenant() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
