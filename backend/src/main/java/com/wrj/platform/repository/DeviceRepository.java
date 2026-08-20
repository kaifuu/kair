package com.wrj.platform.repository;

import com.wrj.platform.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long>, JpaSpecificationExecutor<Device> {

    Optional<Device> findByCode(String code);

    List<Device> findByCategory(Device.Category category);

    boolean existsByCode(String code);

    List<Device> findByVirtualTrueAndCategory(Device.Category category);

    List<Device> findByVirtualTrue();

    /** Modbus TCP 从站单元号寻址(网关 9529 端口) */
    Optional<Device> findByModbusUnitId(Integer modbusUnitId);
}
