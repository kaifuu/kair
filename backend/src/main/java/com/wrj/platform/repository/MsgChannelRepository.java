package com.wrj.platform.repository;

import com.wrj.platform.entity.MsgChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MsgChannelRepository extends JpaRepository<MsgChannel, Long> {

    Optional<MsgChannel> findByCode(String code);

    Optional<MsgChannel> findByType(String type);

    List<MsgChannel> findAllByOrderBySortAscIdAsc();
}
