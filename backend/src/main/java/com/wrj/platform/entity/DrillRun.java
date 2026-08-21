package com.wrj.platform.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 攻防演练记录:一次演练从开始到结束(全部敌机处置/撤离或手动中止)的汇总,
 * detailJson 保存逐架敌机的处置明细(供演练记录回看)。
 */
@Entity
@Table(name = "drill_run")
public class DrillRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    /** RUNNING / COMPLETED / ABORTED */
    private String status;

    /** 是否启用了 AI 自动守候 */
    private Boolean autoguard;

    private Integer enemiesTotal = 0;    // 投放敌机总数
    private Integer detected = 0;        // 被探测发现数
    private Integer neutralized = 0;     // 处置成功数(击落/捕获/驱离)
    private Integer escaped = 0;         // 未处置离场数
    private Long avgResponseMs = 0L;     // 平均响应耗时(发现→首次处置)
    private Integer score = 0;           // 综合评分 0-100

    /** 逐架敌机处置明细 JSON:[{id,kind,outcome,detectedBy,responseMs}] */
    @Column(columnDefinition = "text")
    private String detailJson;

    public DrillRun() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getAutoguard() { return autoguard; }
    public void setAutoguard(Boolean autoguard) { this.autoguard = autoguard; }

    public Integer getEnemiesTotal() { return enemiesTotal; }
    public void setEnemiesTotal(Integer enemiesTotal) { this.enemiesTotal = enemiesTotal; }

    public Integer getDetected() { return detected; }
    public void setDetected(Integer detected) { this.detected = detected; }

    public Integer getNeutralized() { return neutralized; }
    public void setNeutralized(Integer neutralized) { this.neutralized = neutralized; }

    public Integer getEscaped() { return escaped; }
    public void setEscaped(Integer escaped) { this.escaped = escaped; }

    public Long getAvgResponseMs() { return avgResponseMs; }
    public void setAvgResponseMs(Long avgResponseMs) { this.avgResponseMs = avgResponseMs; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
}
