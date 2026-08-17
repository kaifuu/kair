package com.wrj.platform.repository;

import com.wrj.platform.entity.GeoFence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeoFenceRepository extends JpaRepository<GeoFence, Long> {

    List<GeoFence> findByEnabledTrue();
}
