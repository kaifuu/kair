package com.wrj.platform.repository;

import com.wrj.platform.entity.Pilot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PilotRepository extends JpaRepository<Pilot, Long> {

    Optional<Pilot> findByLicenseNo(String licenseNo);

    boolean existsByLicenseNo(String licenseNo);
}
