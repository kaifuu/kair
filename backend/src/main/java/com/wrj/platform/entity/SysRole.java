package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 角色:menuIdsJson 为授权菜单 id 数组;code=ADMIN 特判拥有全部菜单 */
@Entity
@Table(name = "sys_role")
public class SysRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String name;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(length = 255)
    private String remark;

    /** 授权菜单 id 数组 JSON,如 [1,2,3] */
    @Column(length = 2000)
    private String menuIdsJson = "[]";

    private Boolean enabled = true;

    private LocalDateTime createdAt = LocalDateTime.now();

    public SysRole() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public String getMenuIdsJson() { return menuIdsJson; }
    public void setMenuIdsJson(String menuIdsJson) { this.menuIdsJson = menuIdsJson; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
