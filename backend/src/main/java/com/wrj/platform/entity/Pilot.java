package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 飞手(操作员)档案 */
@Entity
@Table(name = "pilot")
public class Pilot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String licenseNo;     // 执照编号

    @Column(nullable = false, length = 32)
    private String name;

    private String phone;
    private String org;           // 所属单位
    private String licenseType;   // 执照类型:多旋翼/固定翼/直升机/垂直起降固定翼
    private Integer licenseGrade; // 等级: IV / III / II / I
    private LocalDate licenseIssue;   // 颁发日期
    private LocalDate licenseExpiry;  // 有效期至
    private Double totalFlightHours = 0.0;
    private Integer totalFlights = 0;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Status { ACTIVE, SUSPENDED, EXPIRED }

    public Pilot() {
    }

    public Pilot(String licenseNo, String name, String phone, String org) {
        this.licenseNo = licenseNo;
        this.name = name;
        this.phone = phone;
        this.org = org;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLicenseNo() { return licenseNo; }
    public void setLicenseNo(String licenseNo) { this.licenseNo = licenseNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getOrg() { return org; }
    public void setOrg(String org) { this.org = org; }

    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }

    public Integer getLicenseGrade() { return licenseGrade; }
    public void setLicenseGrade(Integer licenseGrade) { this.licenseGrade = licenseGrade; }

    public LocalDate getLicenseIssue() { return licenseIssue; }
    public void setLicenseIssue(LocalDate licenseIssue) { this.licenseIssue = licenseIssue; }

    public LocalDate getLicenseExpiry() { return licenseExpiry; }
    public void setLicenseExpiry(LocalDate licenseExpiry) { this.licenseExpiry = licenseExpiry; }

    public Double getTotalFlightHours() { return totalFlightHours; }
    public void setTotalFlightHours(Double totalFlightHours) { this.totalFlightHours = totalFlightHours; }

    public Integer getTotalFlights() { return totalFlights; }
    public void setTotalFlights(Integer totalFlights) { this.totalFlights = totalFlights; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
