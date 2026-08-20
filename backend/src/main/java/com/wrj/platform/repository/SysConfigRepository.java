package com.wrj.platform.repository;

import com.wrj.platform.entity.SysConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysConfigRepository extends JpaRepository<SysConfig, Long> {

    Optional<SysConfig> findByCfgKey(String cfgKey);

    void deleteByCfgKey(String cfgKey);
}
