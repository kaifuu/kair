package com.wrj.platform.repository;

import com.wrj.platform.entity.LlmModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LlmModelRepository extends JpaRepository<LlmModel, Long> {

    List<LlmModel> findAllByOrderByIsDefaultDescUpdatedAtDesc();

    Optional<LlmModel> findFirstByIsDefaultTrueAndEnabledTrue();

    Optional<LlmModel> findFirstByEnabledTrueOrderByUpdatedAtDesc();
}
