package com.wrj.platform.repository;

import com.wrj.platform.entity.DeviceDataHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface DeviceDataHistoryRepository extends JpaRepository<DeviceDataHistory, Long> {

    /** 时间窗内按时间升序取数(倒序取页再内存反转,避免大偏移分页) */
    @Query("SELECT h FROM DeviceDataHistory h WHERE h.deviceId = :deviceId AND h.ts >= :since ORDER BY h.id DESC")
    List<DeviceDataHistory> findRecent(@Param("deviceId") Long deviceId,
                                       @Param("since") LocalDateTime since,
                                       Pageable pageable);

    /** 最新一条(地图气泡/传感面板初值) */
    DeviceDataHistory findFirstByDeviceIdOrderByTsDesc(Long deviceId);

    /** 各设备最新一条(物联网面板批量初值) */
    @Query("SELECT h FROM DeviceDataHistory h WHERE h.id IN (" +
            "SELECT MAX(g.id) FROM DeviceDataHistory g GROUP BY g.deviceId)")
    List<DeviceDataHistory> findLatestPerDevice();

    /** 每设备只保留最近 keep 条(历史数据防膨胀;@Modifying 删除须自带事务,网关线程无事务上下文) */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM device_data_history WHERE device_id = :deviceId AND id NOT IN " +
            "(SELECT id FROM (SELECT id FROM device_data_history WHERE device_id = :deviceId " +
            "ORDER BY id DESC LIMIT :keep) t)", nativeQuery = true)
    int trimPerDevice(@Param("deviceId") Long deviceId, @Param("keep") int keep);

    long countByDeviceId(Long deviceId);
}
