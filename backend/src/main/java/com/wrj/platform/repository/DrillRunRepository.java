package com.wrj.platform.repository;

import com.wrj.platform.entity.DrillRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DrillRunRepository extends JpaRepository<DrillRun, Long> {

    List<DrillRun> findTop20ByOrderByStartedAtDesc();
}
