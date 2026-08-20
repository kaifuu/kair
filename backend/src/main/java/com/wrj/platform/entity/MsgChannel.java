package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 消息通道配置:极光/友盟 APP 推送、邮件、短信、站内信 */
@Entity
@Table(name = "msg_channel", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
public class MsgChannel {

    public static final String TYPE_JPUSH = "JPUSH";
    public static final String TYPE_UMENG = "UMENG";
    public static final String TYPE_EMAIL = "EMAIL";
    public static final String TYPE_SMS = "SMS";
    public static final String TYPE_INAPP = "INAPP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 唯一编码,内置通道固定:jpush/umeng/email/sms/inapp */
    @Column(nullable = false, length = 32)
    private String code;

    /** 通道类型:JPUSH / UMENG / EMAIL / SMS / INAPP */
    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false, length = 64)
    private String name;

    /**
     * 通道参数 JSON(密钥不入代码仓库,与地图密钥同策略):
     * JPUSH  {appKey, masterSecret}
     * UMENG  {appKey, appMasterSecret}
     * EMAIL  {host, port, username, password, from, ssl, testTo}
     * SMS    {apiUrl, method, headers{}, bodyTemplate(${phone}/${content}), successContains, testPhone}
     * INAPP  {}(无参数)
     */
    @Column(columnDefinition = "text")
    private String configJson = "{}";

    private Boolean enabled = false;

    private Integer sort = 0;

    @Column(length = 255)
    private String remark;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public MsgChannel() {
    }

    public MsgChannel(String code, String type, String name, int sort) {
        this.code = code;
        this.type = type;
        this.name = name;
        this.sort = sort;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
