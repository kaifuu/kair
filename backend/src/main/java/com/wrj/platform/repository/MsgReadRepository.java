package com.wrj.platform.repository;

import com.wrj.platform.entity.MsgRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MsgReadRepository extends JpaRepository<MsgRead, Long> {

    Optional<MsgRead> findByMessageIdAndUsername(Long messageId, String username);
}
