package com.wrj.platform.repository;

import com.wrj.platform.entity.SysTenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysTenantRepository extends JpaRepository<SysTenant, Long> {

    boolean existsByCode(String code);
}
