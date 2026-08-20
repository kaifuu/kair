package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 大模型接入配置:GLM/QWEN/DEEPSEEK/本地(Ollama)/自定义,均为 OpenAI 兼容协议 */
@Entity
@Table(name = "llm_model")
public class LlmModel {

    public static final String PROVIDER_GLM = "GLM";
    public static final String PROVIDER_QWEN = "QWEN";
    public static final String PROVIDER_DEEPSEEK = "DEEPSEEK";
    public static final String PROVIDER_LOCAL = "LOCAL";
    public static final String PROVIDER_CUSTOM = "CUSTOM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    /** GLM / QWEN / DEEPSEEK / LOCAL(Ollama 等) / CUSTOM */
    @Column(nullable = false, length = 16)
    private String provider;

    /** OpenAI 兼容基地址,如 https://open.bigmodel.cn/api/paas/v4 */
    @Column(nullable = false, length = 255)
    private String baseUrl;

    /** API Key(本地模型可空) */
    @Column(length = 255)
    private String apiKey;

    /** 模型标识,如 glm-4.5 / qwen-plus / deepseek-chat */
    @Column(nullable = false, length = 64)
    private String modelCode;

    /** 参数:{temperature, maxTokens, timeoutSeconds} */
    @Column(columnDefinition = "text")
    private String paramsJson = "{}";

    private Boolean enabled = false;

    /** 平台默认模型(对话未指定时使用) */
    private Boolean isDefault = false;

    @Column(length = 255)
    private String remark;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public LlmModel() {
    }

    public LlmModel(String name, String provider, String baseUrl, String modelCode) {
        this.name = name;
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.modelCode = modelCode;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }

    public String getParamsJson() { return paramsJson; }
    public void setParamsJson(String paramsJson) { this.paramsJson = paramsJson; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
