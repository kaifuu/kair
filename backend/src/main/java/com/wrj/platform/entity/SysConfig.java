package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 系统配置 KV:当前承载「地图管理」页保存的底图密钥等页面化配置 */
@Entity
@Table(name = "sys_config")
public class SysConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cfg_key", nullable = false, unique = true, length = 64)
    private String cfgKey;

    @Column(name = "cfg_value", length = 512)
    private String cfgValue;

    private LocalDateTime updatedAt = LocalDateTime.now();

    public SysConfig() {
    }

    public SysConfig(String cfgKey, String cfgValue) {
        this.cfgKey = cfgKey;
        this.cfgValue = cfgValue;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCfgKey() { return cfgKey; }
    public void setCfgKey(String cfgKey) { this.cfgKey = cfgKey; }

    public String getCfgValue() { return cfgValue; }
    public void setCfgValue(String cfgValue) { this.cfgValue = cfgValue; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
