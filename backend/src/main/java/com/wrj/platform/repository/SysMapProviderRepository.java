package com.wrj.platform.repository;

import com.wrj.platform.entity.SysMapProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysMapProviderRepository extends JpaRepository<SysMapProvider, Long> {

    Optional<SysMapProvider> findByCode(String code);

    List<SysMapProvider> findAllByOrderBySortAscIdAsc();

    Optional<SysMapProvider> findByIsDefaultTrueAndEnabledTrue();
}
