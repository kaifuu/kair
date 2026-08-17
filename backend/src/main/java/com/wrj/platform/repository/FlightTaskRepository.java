package com.wrj.platform.repository;

import com.wrj.platform.entity.FlightTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlightTaskRepository extends JpaRepository<FlightTask, Long> {

    List<FlightTask> findByStatus(FlightTask.Status status);

    List<FlightTask> findByDroneId(Long droneId);
}
