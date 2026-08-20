package com.wrj.platform.repository;

import com.wrj.platform.entity.SysMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysMenuRepository extends JpaRepository<SysMenu, Long> {

    List<SysMenu> findByEnabledTrueOrderBySort();

    Optional<SysMenu> findFirstByPath(String path);
}
