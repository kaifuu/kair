package com.wrj.platform.repository;

import com.wrj.platform.entity.ModbusRegister;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModbusRegisterRepository extends JpaRepository<ModbusRegister, Long> {

    List<ModbusRegister> findAllByUnitId(Integer unitId);
}
