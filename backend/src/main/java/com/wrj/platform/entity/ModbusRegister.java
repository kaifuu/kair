package com.wrj.platform.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Modbus 寄存器持久值:FC16 写入落库,重启后回加载,FC3/4 读取不再归零 */
@Entity
@Table(name = "modbus_register", uniqueConstraints = @UniqueConstraint(columnNames = {"unitId", "addr"}))
public class ModbusRegister {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Modbus 从站单元号 */
    @Column(nullable = false)
    private Integer unitId;

    /** 寄存器地址 */
    @Column(nullable = false)
    private Integer addr;

    /** 16 位寄存器值(0..65535) */
    @Column(nullable = false)
    private Integer value;

    private LocalDateTime updatedAt = LocalDateTime.now();

    public ModbusRegister() {
    }

    public ModbusRegister(Integer unitId, Integer addr, Integer value) {
        this.unitId = unitId;
        this.addr = addr;
        this.value = value;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getUnitId() { return unitId; }
    public void setUnitId(Integer unitId) { this.unitId = unitId; }

    public Integer getAddr() { return addr; }
    public void setAddr(Integer addr) { this.addr = addr; }

    public Integer getValue() { return value; }
    public void setValue(Integer value) { this.value = value; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
