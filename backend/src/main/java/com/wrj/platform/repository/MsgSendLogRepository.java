package com.wrj.platform.repository;

import com.wrj.platform.entity.MsgSendLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MsgSendLogRepository extends JpaRepository<MsgSendLog, Long> {

    Page<MsgSendLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<MsgSendLog> findByChannelTypeOrderByCreatedAtDesc(String channelType, Pageable pageable);
}
