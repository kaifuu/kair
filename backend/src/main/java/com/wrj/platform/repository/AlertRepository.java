package com.wrj.platform.repository;

import com.wrj.platform.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Page<Alert> findByHandledFalse(Pageable pageable);

    Page<Alert> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Alert> findAllByOrderByCreatedAtDesc();

    List<Alert> findByHandledFalse();

    List<Alert> findTop50ByOrderByCreatedAtDesc();

    long countByHandledFalse();

    long countByHandledTrue();

    /** 派生删除,需在事务内调用 */
    long deleteByHandledTrue();

    long countByLevelAndHandledFalse(Alert.Level level);
}
