package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 底图厂商配置(地图管理 CRUD):
 * - vendor:厂商(BAIDU/AMAP/TDT/CUSTOM 自定义瓦片)
 * - credentialsJson:厂商凭证(BAIDU {"ak"} / AMAP {"key","secret"} / TDT {"tk"} / CUSTOM {"key"} 可空)
 * - tileUrl/engine/grad:CUSTOM 专有(瓦片模板 / 渲染引擎 tdt|amap|baidu / 卡片渐变色)
 * - isDefault:平台默认底图(全平台唯一,登录后未本机选择时的缺省图源)
 */
@Entity
@Table(name = "sys_map_provider")
public class SysMapProvider {

    public static final String VENDOR_BAIDU = "BAIDU";
    public static final String VENDOR_AMAP = "AMAP";
    public static final String VENDOR_TDT = "TDT";
    public static final String VENDOR_CUSTOM = "CUSTOM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 提供商标识(baidu/amap/tdt/custom-xxx),内置三家固定,自定义可增 */
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 16)
    private String vendor = VENDOR_CUSTOM;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(length = 2000)
    private String credentialsJson = "{}";

    /** CUSTOM:瓦片地址模板,含 {z}/{x}/{y},可含 {key} 占位 */
    @Column(length = 500)
    private String tileUrl;

    /** CUSTOM:渲染引擎 tdt|amap|baidu */
    @Column(length = 16)
    private String engine;

    /** 卡片渐变色(CSS linear-gradient 参数) */
    @Column(length = 128)
    private String grad;

    private Boolean enabled = true;

    private Boolean isDefault = false;

    private Integer sort = 0;

    private LocalDateTime createdAt = LocalDateTime.now();

    public SysMapProvider() {
    }

    public SysMapProvider(String code, String vendor, String name) {
        this.code = code;
        this.vendor = vendor;
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCredentialsJson() { return credentialsJson; }
    public void setCredentialsJson(String credentialsJson) { this.credentialsJson = credentialsJson; }

    public String getTileUrl() { return tileUrl; }
    public void setTileUrl(String tileUrl) { this.tileUrl = tileUrl; }

    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }

    public String getGrad() { return grad; }
    public void setGrad(String grad) { this.grad = grad; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
