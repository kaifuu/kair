package com.wrj.platform.repository;

import com.wrj.platform.entity.DeviceMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeviceMessageRepository extends JpaRepository<DeviceMessage, Long> {

    @Query("SELECT m FROM DeviceMessage m WHERE (:deviceId IS NULL OR m.deviceId = :deviceId) " +
            "AND (:direction IS NULL OR m.direction = :direction) " +
            "AND (:frameType IS NULL OR m.frameType = :frameType) ORDER BY m.id DESC")
    Page<DeviceMessage> search(@Param("deviceId") Long deviceId,
                               @Param("direction") DeviceMessage.Direction direction,
                               @Param("frameType") String frameType,
                               Pageable pageable);

    /** 最老的一批 id(裁剪超出保留上限的记录用) */
    @Query("SELECT m.id FROM DeviceMessage m ORDER BY m.id ASC")
    List<Long> findIdsAsc(Pageable pageable);
}
