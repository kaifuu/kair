package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 模型调用用量日志:Token 统计监控数据源 */
@Entity
@Table(name = "llm_usage_log", indexes = @Index(columnList = "createdAt"))
public class LlmUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long modelId;

    @Column(length = 64)
    private String modelName;

    @Column(length = 16)
    private String provider;

    /** 调用场景:CHAT / TEST / 其他业务标记 */
    @Column(length = 32)
    private String scene;

    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;

    private Long durationMs;

    /** SUCCESS / FAIL */
    @Column(length = 16)
    private String status;

    @Column(length = 500)
    private String error;

    private LocalDateTime createdAt = LocalDateTime.now();

    public LlmUsageLog() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }

    public Long getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Long promptTokens) { this.promptTokens = promptTokens; }

    public Long getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Long completionTokens) { this.completionTokens = completionTokens; }

    public Long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
