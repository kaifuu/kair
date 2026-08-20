package com.wrj.platform.repository;

import com.wrj.platform.entity.ProtocolTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProtocolRepository extends JpaRepository<ProtocolTemplate, Long> {
}
