package com.wrj.platform.repository;

import com.wrj.platform.entity.SysLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysLogRepository extends JpaRepository<SysLog, Long> {

    @Query("SELECT l FROM SysLog l WHERE (:type IS NULL OR l.type = :type) " +
            "AND (:keyword IS NULL OR l.username LIKE %:keyword OR l.action LIKE %:keyword " +
            "OR l.detail LIKE %:keyword) ORDER BY l.createdAt DESC")
    Page<SysLog> search(@Param("type") SysLog.Type type,
                        @Param("keyword") String keyword,
                        Pageable pageable);

    long countByType(SysLog.Type type);
}
