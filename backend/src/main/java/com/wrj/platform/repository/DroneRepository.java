package com.wrj.platform.repository;

import com.wrj.platform.entity.Drone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DroneRepository extends JpaRepository<Drone, Long> {

    Optional<Drone> findByCode(String code);

    List<Drone> findByStatus(Drone.Status status);

    boolean existsByCode(String code);
}
